package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.service.AnaDataClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Porta do coletor REST da ANA (projeto-mapi/src/collectors/ana_rest_collector.py +
 * services/auth_manager.py). Ao contrário do Python original, NÃO envia o cookie estático que
 * existia lá — era um artefato de sessão de navegador hardcoded, dispensável para o fluxo OAuth
 * (correção de segurança pedida na migração para dentro da API).
 */
@Service
@Slf4j
public class AnaDataClientImpl implements AnaDataClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";
    private static final String REFERER = "https://www.ana.gov.br/hidrowebservice/swagger-ui/index.html";

    private final RestClient restClient;
    private final AppProperties.Ana config;
    private final ObjectMapper objectMapper;

    private volatile String cachedToken;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;
    private final Map<String, JsonNode> inventoryCache = new ConcurrentHashMap<>();

    public AnaDataClientImpl(RestClient.Builder restClientBuilder, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.config = appProperties.getAna();
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "anaData")
    @CircuitBreaker(name = "anaData", fallbackMethod = "fetchStationMeasurementsFallback")
    public List<JsonNode> fetchStationMeasurements(String stationCode, String searchDate) {
        String token = ensureToken();
        if (token == null) {
            log.warn("Sem token ANA válido; abortando coleta da estação {}", stationCode);
            return List.of();
        }

        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("Código da Estação", stationCode);
            params.add("Tipo Filtro Data", config.getTipoFiltroData());
            params.add("Data de Busca (yyyy-MM-dd)", searchDate);
            params.add("Range Intervalo de busca", config.getIntervaloBusca());

            JsonNode medicao = getJson(config.getBaseUrl(), params, token);
            JsonNode items = medicao.path("items");
            if (!items.isArray() || items.isEmpty()) {
                return List.of();
            }

            JsonNode inventario = fetchInventory(stationCode, token);
            List<JsonNode> resultado = new ArrayList<>();
            for (JsonNode item : items) {
                if (item.isObject() && inventario != null) {
                    ObjectNode enriched = (ObjectNode) item;
                    enriched.set("Latitude", inventario.path("Latitude"));
                    enriched.set("Longitude", inventario.path("Longitude"));
                    enriched.set("Estacao_Nome", inventario.path("Estacao_Nome"));
                    enriched.set("Bacia_Nome", inventario.path("Bacia_Nome"));
                    enriched.set("Municipio_Nome", inventario.path("Municipio_Nome"));
                }
                resultado.add(item);
            }
            return resultado;
        } catch (HttpClientErrorException.Unauthorized e) {
            log.warn("Token ANA rejeitado (401) para estação {}; invalidando cache para nova tentativa.", stationCode);
            cachedToken = null;
            tokenExpiresAt = Instant.EPOCH;
            throw e;
        }
    }

    private List<JsonNode> fetchStationMeasurementsFallback(String stationCode, String searchDate, Throwable t) {
        log.warn("Falha ao coletar dados ANA da estação {}: {} — {}", stationCode, t.getClass().getSimpleName(), t.getMessage());
        return List.of();
    }

    private JsonNode fetchInventory(String stationCode, String token) {
        JsonNode cached = inventoryCache.get(stationCode);
        if (cached != null) {
            return cached;
        }
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("Código da Estação", stationCode);
            JsonNode data = getJson(config.getInventoryUrl(), params, token);
            JsonNode items = data.path("items");
            if (items.isArray() && !items.isEmpty()) {
                JsonNode metadata = items.get(0);
                inventoryCache.put(stationCode, metadata);
                return metadata;
            }
        } catch (Exception e) {
            log.warn("Erro ao buscar inventário ANA para {}: {}", stationCode, e.getMessage());
        }
        return null;
    }

    private synchronized String ensureToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }
        if (config.getIdentifier() == null || config.getIdentifier().isBlank()) {
            log.warn("ANA_IDENTIFICADOR/ANA_SENHA não configurados; coleta ANA desativada até serem definidos.");
            return null;
        }
        try {
            String body = restClient.get()
                    .uri(URI.create(config.getAuthUrl()))
                    .header("identificador", config.getIdentifier())
                    .header("senha", config.getPassword())
                    .header("accept", "application/json")
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(body);
            String token = root.path("items").path("tokenautenticacao").asText(null);
            if (token != null && !token.isBlank()) {
                cachedToken = token;
                tokenExpiresAt = Instant.now().plus(59, ChronoUnit.MINUTES);
                log.info("Token ANA renovado com sucesso (válido por 59min).");
                return cachedToken;
            }
            log.warn("Resposta de autenticação ANA sem token válido.");
        } catch (Exception e) {
            log.error("Erro ao obter token ANA: {}", e.getMessage());
        }
        return null;
    }

    private JsonNode getJson(String url, MultiValueMap<String, String> params, String token) {
        URI uri = UriComponentsBuilder.fromHttpUrl(url)
                .queryParams(params)
                .build()
                .encode()
                .toUri();

        String body = restClient.get()
                .uri(uri)
                .header("accept", "*/*")
                .header("authorization", "Bearer " + token)
                .header("referer", REFERER)
                .header("user-agent", USER_AGENT)
                .retrieve()
                .body(String.class);

        try {
            return objectMapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Resposta inválida da ANA em " + url + ": " + e.getMessage(), e);
        }
    }
}
