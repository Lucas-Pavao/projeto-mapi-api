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
    public record DadosLimpeza(LocalDate ultimaLimpeza, double dias, double lambdaRePorDia) {}

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final Pattern ANO = Pattern.compile("^(\\d{4})");

    private final LimpezaProperties props;
    private final LimpezaRepository repo;
    private final FloodPointRepository points;
    private final ExternalRestClientFactory http;
    private final ObjectMapper mapper;
    private final Clock clock;

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
            for (Central156.Chamado c : cs) {
                if (!servicos.contains(c.groupCode() + "/" + c.serviceCode())) continue;
                doServico++;
                for (Central156.Casamento m : Central156.casar(c, refs, props.getRaioM())) {
                    casamentos++;
                    gravados += repo.upsert(c, m);
                }
            }
        }
        return new Resultado(new ArrayList<>(porAno.keySet()), lidos, doServico, casamentos, gravados);
    }

    /** Dias desde a última limpeza conhecida e a taxa do ponto; vazio se faltar um dos dois ou a taxa tiver poucas limpezas. */
    public Optional<DadosLimpeza> dados(FloodPoint p) {
        if (!props.isEnabled()) return Optional.empty();
        LimpezaProperties.Ponto cfg = props.getPontos().get(p.getSlug());
        if (cfg == null || cfg.getLambdaRePorDia() == null || cfg.getLimpezas() < props.getMinLimpezas()) return Optional.empty();
        Optional<LocalDate> ultima;
        try {
            ultima = repo.ultimaLimpeza(p.getId());
        } catch (Exception e) {
            return Optional.empty();
        }
        if (ultima.isEmpty()) return Optional.empty();
        double dias = Math.max(0, ChronoUnit.DAYS.between(ultima.get(), LocalDate.now(clock.withZone(RECIFE))));
        return Optional.of(new DadosLimpeza(ultima.get(), dias, cfg.getLambdaRePorDia()));
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
