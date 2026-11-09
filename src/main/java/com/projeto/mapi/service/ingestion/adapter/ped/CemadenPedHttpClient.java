package com.projeto.mapi.service.ingestion.adapter.ped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

/**
 * HTTP da PED do Cemaden. Três regras da plataforma viram código aqui:
 * <ul>
 *   <li>limite de 12 requisições por minuto para usuário externo, com bloqueio de quem passa: uma requisição a cada
 *       {@code minIntervalMs} (6 s = 10 por minuto), contando também a emissão de token, sem rajada;</li>
 *   <li>token JWT com validade: renovado antes de vencer (claim {@code exp}) e, se a PED responder 401/403, renovado
 *       uma vez e a requisição repetida. Expiração é erro recuperável, nunca "zero registros";</li>
 *   <li>HTTP 202 com "Nenhum resultado foi encontrado" = sem dado no intervalo ({@link Response#noData()}).</li>
 * </ul>
 * Erro transitório (rede, 429, 5xx) tenta de novo com espera crescente; esgotadas as tentativas, falha visível.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CemadenPedHttpClient implements CemadenPedClient {

    static final String CLIENT = "cemaden-ped";
    static final String NO_RESULT = "Nenhum resultado foi encontrado";
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyyMMddHHmm").withZone(ZoneOffset.UTC);
    /** Renova o token quando faltar menos que isso para vencer. */
    private static final Duration TOKEN_MARGIN = Duration.ofMinutes(5);

    private final ExternalRestClientFactory http;
    private final IngestionProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;
    /** Quando pedir token de novo: margem antes do vencimento, ou o próprio vencimento se a PED repetir o token. */
    private Instant renewAt = Instant.EPOCH;
    private long lastRequestNanos;

    @Override
    public synchronized Response registry(String ibgeCode) {
        String url = UriComponentsBuilder.fromUriString(cfg().getBaseUrl() + "/pcds-cadastro/dados-cadastrais")
                .queryParam("codibge", ibgeCode).build().toUriString();
        return get(url);
    }

    @Override
    public synchronized Response rain(String stationCode, Instant from, Instant to) {
        String url = UriComponentsBuilder.fromUriString(cfg().getBaseUrl() + "/pcds/dados_pcd")
                .queryParam("rede", "11").queryParam("codigo", stationCode).queryParam("sensor", "10")
                .queryParam("inicio", TS.format(from)).queryParam("fim", TS.format(to)).build().toUriString();
        return get(url);
    }

    private IngestionProperties.CemadenPed cfg() {
        return props.getCemadenPed();
    }

    private Response get(String url) {
        RuntimeException last = null;
        boolean renewed = false;
        for (int attempt = 1; attempt <= cfg().getMaxAttempts(); attempt++) {
            try {
                String tok = ensureToken();
                throttle();
                Response r = client().get().uri(url).header("token", tok).exchange((req, res) -> {
                    int status = res.getStatusCode().value();
                    String body = new String(res.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    return new Response(status, url, body, status == 202 || body.contains(NO_RESULT));
                }, true);
                int s = r.httpStatus();
                if ((s == 401 || s == 403) && !renewed) {
                    log.warn("PED {}: token recusado, renovando", s);
                    tokenExpiresAt = Instant.EPOCH;
                    renewAt = Instant.EPOCH;
                    renewed = true;
                    attempt--; // renovar o token não conta como tentativa
                    continue;
                }
                if (s == 200 || s == 202) return r;
                if (s == 429 || s >= 500) {
                    last = new SourceUnavailableException("PED HTTP " + s + " em " + url + ": " + abbrev(r.body()));
                    backoff(attempt);
                    continue;
                }
                throw new SourceUnavailableException("PED HTTP " + s + " em " + url + ": " + abbrev(r.body()));
            } catch (SourceUnavailableException e) {
                throw e;
            } catch (RuntimeException e) {
                last = e;
                log.warn("PED: falha transitória na tentativa {} ({}): {}", attempt, url, e.toString());
                backoff(attempt);
            }
        }
        throw new SourceUnavailableException("PED indisponível depois de " + cfg().getMaxAttempts() + " tentativas: "
                + url, last);
    }

    /** Token vigente; emite outro se não houver ou se estiver perto de vencer. */
    private String ensureToken() {
        if (token != null && clock.instant().isBefore(renewAt)) return token;
        if (cfg().getEmail() == null || cfg().getEmail().isBlank() || cfg().getPassword() == null
                || cfg().getPassword().isBlank()) {
            throw new SourceUnavailableException("PED: credenciais ausentes (CEMADEN_PED_EMAIL / CEMADEN_PED_SENHA)");
        }
        throttle();
        String body;
        try {
            body = client().post().uri(cfg().getTokenUrl()).contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("email", cfg().getEmail(), "password", cfg().getPassword()))
                    .retrieve().onStatus(HttpStatusCode::isError, (req, res) -> {
                        int st = res.getStatusCode().value();
                        if (st == 429 || st >= 500) throw new TransientPedException("PED: emissão de token HTTP " + st);
                        throw new SourceUnavailableException("PED: emissão de token recusada (HTTP " + st + ")");
                    }).body(String.class);
        } catch (SourceUnavailableException | TransientPedException e) {
            throw e;
        } catch (RuntimeException e) {
            // rede (timeout de conexão etc.): transitória, entra nas tentativas com espera de get()
            throw new TransientPedException("PED: emissão de token falhou: " + e.getMessage(), e);
        }
        try {
            JsonNode n = mapper.readTree(body);
            String t = n.path("token").asText(null);
            if (t == null || t.isBlank()) throw new SourceUnavailableException("PED: resposta de token sem campo token");
            token = t;
            tokenExpiresAt = expiryOf(t, clock.instant());
            // A PED devolve o MESMO token enquanto ele vale (medido em 2026-11-09): dentro da margem, pedir de novo a
            // cada requisição só gasta o limite de 10/min. Usa até vencer; um 401/403 força a renovação.
            renewAt = renewAtFor(tokenExpiresAt, clock.instant());
            log.info("PED: token emitido, vence em {}", tokenExpiresAt);
            return token;
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceUnavailableException("PED: resposta de token ilegível", e);
        }
    }

    /** Falha de rede ou 5xx: tenta de novo com espera, em vez de encerrar o run (medido em 2026-11-09: connect timeout). */
    static class TransientPedException extends RuntimeException {
        TransientPedException(String msg) {
            super(msg);
        }

        TransientPedException(String msg, Throwable cause) {
            super(msg, cause);
        }
    }

    /** Renova na margem antes do vencimento; se já está dentro da margem, só depois de vencer. */
    static Instant renewAtFor(Instant expiresAt, Instant now) {
        Instant early = expiresAt.minus(TOKEN_MARGIN);
        return now.isBefore(early) ? early : expiresAt.plusSeconds(1);
    }

    /** {@code exp} do JWT; sem ele, assume 1 h (renovação por 401/403 cobre o erro). */
    static Instant expiryOf(String jwt, Instant now) {
        try {
            String[] parts = jwt.split("\\.");
            JsonNode claims = new ObjectMapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (claims.has("exp")) return Instant.ofEpochSecond(claims.get("exp").asLong());
        } catch (Exception ignored) {
            // segue com o padrão
        }
        return now.plus(Duration.ofHours(1));
    }

    private void throttle() {
        long gap = cfg().getMinIntervalMs() * 1_000_000L;
        long wait = lastRequestNanos == 0 ? 0 : lastRequestNanos + gap - System.nanoTime();
        if (wait > 0) sleep(wait / 1_000_000L + 1);
        lastRequestNanos = System.nanoTime();
    }

    private void backoff(int attempt) {
        sleep(cfg().getRetryBackoffMs() * (1L << Math.min(attempt - 1, 4)));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("PED: interrompido", e);
        }
    }

    private RestClient restClient;

    private RestClient client() {
        if (restClient == null) restClient = http.builder(CLIENT).build();
        return restClient;
    }

    private static String abbrev(String s) {
        return s == null ? "" : s.length() > 200 ? s.substring(0, 200) : s;
    }
}
