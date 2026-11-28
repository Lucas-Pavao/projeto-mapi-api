package com.projeto.mapi.service.limpeza;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.LimpezaProperties;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.limpeza.LimpezaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Ingestão das limpezas da Central 156 (CSV anual do CKAN, atualização semanal) e dados de limpeza para o alerta.
 * O conjunto "Dados Vivos" da 156 não é usado: em 2026-11-21 ele estava parado (103 registros, todos de 2025-01-23).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LimpezaService {

    public record Resultado(List<Integer> anos, int lidos, int doServico, int casamentos, int gravados) {}

    /** Dados de limpeza de um ponto para o balde; vazio = sem dado (comportamento de sempre). */
    /**
     * Dados de limpeza do ponto para o alerta (emenda 1 da 156). {@code regime}: TEMPO_DESDE_LIMPEZA (usa {@code dias}) ou
     * PERMANENTE (limpeza desconhecida: usa {@code diasEntupidaMedia}; {@code dias} nulo). {@code motivo} diz por quê.
     */
    public record DadosLimpeza(String regime, LocalDate ultimaLimpeza, Double dias, double lambdaRePorDia,
                               Double diasEntupidaMedia, LocalDate fimCobertura, String motivo) {}

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final Pattern ANO = Pattern.compile("^(\\d{4})");

    private final LimpezaProperties props;
    private final LimpezaRepository repo;
    private final FloodPointRepository points;
    private final ExternalRestClientFactory http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final java.util.Set<String> coordenadasPadrao = carregarCoordenadasPadrao();

    /** E1: coordenadas-padrão da 156 (geradas pelo script da emenda 1; recurso versionado). */
    static java.util.Set<String> carregarCoordenadasPadrao() {
        try (var in = LimpezaService.class.getResourceAsStream("/limpeza/coordenadas_padrao.json")) {
            if (in == null) return java.util.Set.of();
            java.util.Set<String> out = new java.util.HashSet<>();
            for (JsonNode c : new com.fasterxml.jackson.databind.ObjectMapper().readTree(in).path("coordenadas")) {
                out.add(Central156.chaveCoordenada(c.get(0).asDouble(), c.get(1).asDouble()));
            }
            return java.util.Set.copyOf(out);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("coordenadas-padrão da 156 ilegíveis", e);
        }
    }

    @Scheduled(cron = "${mapi.limpeza.cron:0 30 4 * * MON}", zone = "America/Recife")
    public void semanal() {
        if (!props.isEnabled()) return;
        try {
            Resultado r = ingerir(LocalDate.now(clock.withZone(RECIFE)).getYear());
            log.info("Limpezas da 156 ingeridas: {}", r);
        } catch (Exception e) {
            log.warn("Ingestão das limpezas da 156 falhou: {}", e.toString());
        }
    }

    /** Ingere os CSV anuais de {@code anoInicial} até o ano corrente (o recurso mais recente de cada ano). */
    public Resultado ingerir(int anoInicial) throws Exception {
        RestClient ckan = http.client("ckan", props.getCkanBaseUrl(), Map.of());
        String body = ckan.get().uri("/api/3/action/package_show?id={id}", props.getDataset()).retrieve().body(String.class);
        JsonNode res = mapper.readTree(body).path("result").path("resources");
        Map<Integer, JsonNode> porAno = new TreeMap<>();
        for (JsonNode r : res) {
            if (!"CSV".equalsIgnoreCase(r.path("format").asText())) continue;
            Matcher m = ANO.matcher(r.path("name").asText());
            if (!m.find()) continue;
            int ano = Integer.parseInt(m.group(1));
            if (ano < anoInicial) continue;
            JsonNode atual = porAno.get(ano);
            if (atual == null || r.path("last_modified").asText().compareTo(atual.path("last_modified").asText()) > 0) porAno.put(ano, r);
        }
        Set<String> servicos = Set.copyOf(props.getServicos());
        List<Central156.PontoRef> refs = pontosRef();
        int lidos = 0, doServico = 0, casamentos = 0, gravados = 0;
        RestClient livre = http.builder("ckan").build();
        for (var e : porAno.entrySet()) {
            byte[] bytes = livre.get().uri(URI.create(e.getValue().path("url").asText().replace(":443/", "/")))
                    .retrieve().body(byte[].class);
            if (bytes == null) continue;
            List<Central156.Chamado> cs = Central156.parse(new String(bytes, StandardCharsets.UTF_8), e.getKey());
            lidos += cs.size();
            cs.stream().map(Central156.Chamado::demandDate).filter(java.util.Objects::nonNull).max(LocalDate::compareTo)
                    .ifPresent(max -> repo.upsertCobertura(e.getKey(), max, cs.size()));
            for (Central156.Chamado c : cs) {
                if (!servicos.contains(c.groupCode() + "/" + c.serviceCode())) continue;
                doServico++;
                for (Central156.Casamento m : Central156.casar(c, refs, props.getRaioM(), coordenadasPadrao)) {
                    casamentos++;
                    gravados += repo.upsert(c, m);
                }
            }
        }
        return new Resultado(new ArrayList<>(porAno.keySet()), lidos, doServico, casamentos, gravados);
    }

    /**
     * Emenda 1 da 156 (E3/E4). Com taxa do ponto: a última limpeza conhecida vale se {@code fim − última ≤ N_p} e hoje ainda
     * não passou de {@code fim + N_p} (N_p = p95 dos intervalos entre limpezas do ponto); senão, a limpeza é desconhecida e
     * vale o regime permanente. Vazio se faltar a taxa (poucas limpezas) ou, no regime permanente, o tempo médio entupida.
     */
    public Optional<DadosLimpeza> dados(FloodPoint p) {
        if (!props.isEnabled()) return Optional.empty();
        LimpezaProperties.Ponto cfg = props.getPontos().get(p.getSlug());
        if (cfg == null || cfg.getLambdaRePorDia() == null || cfg.getLimpezas() < props.getMinLimpezas()) return Optional.empty();
        Optional<LocalDate> ultima;
        LocalDate fim;
        try {
            ultima = repo.ultimaLimpeza(p.getId());
            fim = repo.fimCobertura().orElse(props.getFimCoberturaPadrao());
        } catch (Exception e) {
            return Optional.empty();
        }
        return Optional.ofNullable(decidir(cfg, ultima.orElse(null), fim, LocalDate.now(clock.withZone(RECIFE))));
    }

    /** Regra E3/E4, pura (testável). */
    static DadosLimpeza decidir(LimpezaProperties.Ponto cfg, LocalDate ultima, LocalDate fim, LocalDate hoje) {
        Integer n95 = cfg.getIntervaloP95Dias();
        String motivo;
        if (ultima == null) motivo = "nenhuma limpeza registrada perto do ponto";
        else if (n95 == null) motivo = "sem intervalo entre limpezas para saber se o registro está completo";
        else if (ChronoUnit.DAYS.between(ultima, fim) > n95)
            motivo = "última limpeza registrada (" + ultima + ") mais antiga que " + n95 + " dias antes do fim da base (" + fim + ")";
        else if (ChronoUnit.DAYS.between(fim, hoje) > n95)
            motivo = "a base da 156 acabou em " + fim + ", há mais de " + n95 + " dias";
        else {
            double dias = Math.max(0, ChronoUnit.DAYS.between(ultima, hoje));
            return new DadosLimpeza("TEMPO_DESDE_LIMPEZA", ultima, dias, cfg.getLambdaRePorDia(), cfg.getDiasEntupidaMedia(), fim,
                    "última limpeza registrada em " + ultima);
        }
        if (cfg.getDiasEntupidaMedia() == null) return null;
        return new DadosLimpeza("PERMANENTE", ultima, null, cfg.getLambdaRePorDia(), cfg.getDiasEntupidaMedia(), fim, motivo);
    }

    public Map<String, Object> resumo() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pontos", repo.resumo());
        out.put("config", props.getPontos().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, e -> Map.of(
                "lambdaRePorDia", String.valueOf(e.getValue().getLambdaRePorDia()), "limpezas", e.getValue().getLimpezas()),
                (a, b) -> a, LinkedHashMap::new)));
        return out;
    }

    private List<Central156.PontoRef> pontosRef() {
        List<Central156.PontoRef> out = new ArrayList<>();
        for (FloodPoint p : points.findAll()) {
            LimpezaProperties.Ponto cfg = props.getPontos().get(p.getSlug());
            if (cfg == null || p.getLatitude() == null || p.getLongitude() == null) continue;
            out.add(new Central156.PontoRef(p.getId(), p.getSlug(), p.getLatitude(), p.getLongitude(),
                    cfg.getLogradouro(), cfg.getBairro()));
        }
        return out;
    }
}
