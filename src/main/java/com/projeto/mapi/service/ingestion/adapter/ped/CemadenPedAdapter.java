package com.projeto.mapi.service.ingestion.adapter.ped;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.ingestion.PedFetchWindowRepository;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.StationInfo;
import com.projeto.mapi.service.ingestion.adapter.SourceAdapter;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Cemaden pela Plataforma de Entrega de Dados (ADR 0021): chuva por bloco das PCDs, carimbo no fim, em UTC.
 *
 * <p>Cada linha {@code chuva} da {@code /pcds/dados_pcd} vira um registro bruto (estação x carimbo) com a linha original
 * e o código de {@code qualificacao}, guardado como qualificador (significado em consulta, docs/extratos/cemaden-ped.md).
 * O bloco tem duração variável (10 min com chuva, 60 min no seco; de 2021 em diante, de 10 a 60 min com chuva): ela é o
 * intervalo até a leitura anterior. Para que a duração não dependa de onde a janela começa, cada requisição começa
 * {@code lookbackMinutes} antes da janela; a leitura anterior a uma leitura da janela, se existe a até
 * {@code lookbackMinutes}, está sempre na resposta. Sem anterior nesse intervalo, o bloco é declarado com
 * {@code lookbackMinutes + 1} min e a validação o manda para a quarentena (AFTER_GAP).
 *
 * <p>Carga retomável: janela gravada em {@code ped_fetch_window} na transação do lote é pulada na execução seguinte.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CemadenPedAdapter implements SourceAdapter {

    public static final String SOURCE = "CEMADEN_PED";
    static final String VARIABLE = "precipitation";
    static final String HEADER_TZ = "PCD com horario UTC";
    static final String COLUMNS = "cod.estacao;nome;municipio;uf;latitude;longitude;datahora;sensor;valor;qualificacao;offset";
    private static final DateTimeFormatter DATAHORA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CemadenPedClient client;
    private final PedFetchWindowRepository windows;
    private final FloodPointRepository floodPoints;
    private final IngestionProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public String source() {
        return SOURCE;
    }

    @Override
    public String declaredTz() {
        return "UTC";
    }

    @Override
    public String parserVersion() {
        return "cemaden-ped/1";
    }

    private IngestionProperties.CemadenPed cfg() {
        return props.getCemadenPed();
    }

    @Override
    public void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink) {
        List<StationInfo> stations = selectedStations();
        log.info("PED: {} estações a até {} m de algum ponto", stations.size(), cfg().getStationSelectionRadiusM());
        Set<String> done = cfg().isResume() ? windows.doneKeys() : Set.of();
        Duration step = Duration.ofDays(cfg().getWindowDays());
        Duration lookback = Duration.ofMinutes(cfg().getLookbackMinutes());
        boolean first = true;
        int skipped = 0;
        for (StationInfo st : stations) {
            Instant installed = instant(st.attrs().path("data_instalacao").asText(null));
            for (Instant w = from; w.isBefore(to); w = w.plus(step)) {
                Instant e = w.plus(step).isAfter(to) ? to : w.plus(step);
                if (installed != null && !e.isAfter(installed)) continue; // antes da instalação: nada a pedir
                if (done.contains(PedFetchWindowRepository.key(st.stationId(), w, e))) {
                    skipped++;
                    continue;
                }
                CemadenPedClient.Response resp = client.rain(st.stationId(), w.minus(lookback), e);
                FetchedBatch b = toBatch(resp, st, w, e, first ? stations : List.of());
                sink.accept(b);
                first = false;
            }
        }
        if (first) {
            // nada baixado (tudo retomado): ainda assim o cadastro é atualizado
            sink.accept(new FetchedBatch(List.of(), stations, mapper.createObjectNode().put("kind", "REGISTRY_ONLY")
                    .put("skippedWindows", skipped)));
        }
        log.info("PED: {} janelas puladas por já estarem carregadas", skipped);
    }

    /** Lote de uma janela: registros com carimbo em [start, end) e a marcação da janela na mesma transação. */
    FetchedBatch toBatch(CemadenPedClient.Response resp, StationInfo st, Instant start, Instant end,
                         List<StationInfo> stations) {
        String sha = Hashing.sha256(resp.body() == null ? "" : resp.body());
        ObjectNode req = mapper.createObjectNode();
        req.put("url", resp.url());
        req.put("httpStatus", resp.httpStatus());
        req.put("sha256", sha);
        req.put("station", st.stationId());
        req.put("windowStart", start.toString());
        req.put("windowEnd", end.toString());
        Instant fetchedAt = clock.instant();
        if (resp.noData()) {
            req.put("records", 0);
            req.put("noData", true);
            return new FetchedBatch(List.of(), stations, req, () -> windows.markDone(st.stationId(), start, end,
                    "NO_DATA", resp.httpStatus(), 0, sha, resp.url(), parserVersion(), fetchedAt));
        }
        List<RawRecord> records = parse(resp.body(), st.stationId(), start, end, fetchedAt);
        req.put("records", records.size());
        return new FetchedBatch(records, stations, req, () -> windows.markDone(st.stationId(), start, end, "OK",
                resp.httpStatus(), records.size(), sha, resp.url(), parserVersion(), fetchedAt));
    }

    /**
     * Linhas {@code chuva} do texto da {@code dados_pcd}. Cabeçalho sem a declaração de UTC ou com colunas diferentes =
     * falha visível: o extrator não confia em layout nem fuso desconhecidos.
     */
    List<RawRecord> parse(String body, String stationId, Instant start, Instant end, Instant collectedAt) {
        String[] lines = body.split("\\r?\\n");
        if (lines.length < 2 || !lines[0].contains(HEADER_TZ) || !lines[1].trim().equals(COLUMNS)) {
            throw new SourceUnavailableException("PED: cabeçalho inesperado para " + stationId + " (\""
                    + (lines.length == 0 ? "" : lines[0]) + "\"); extrator não confia no layout nem no fuso");
        }
        NavigableMap<LocalDateTime, String[]> rows = new TreeMap<>();
        for (int i = 2; i < lines.length; i++) {
            String[] p = lines[i].split(";", -1);
            if (p.length < 10 || !"chuva".equals(p[7]) || !stationId.equals(p[0])) continue;
            rows.put(LocalDateTime.parse(p[6], DATAHORA), p);
        }
        int lookback = cfg().getLookbackMinutes();
        List<RawRecord> out = new ArrayList<>();
        for (Map.Entry<LocalDateTime, String[]> en : rows.entrySet()) {
            Instant ts = en.getKey().toInstant(ZoneOffset.UTC);
            if (ts.isBefore(start) || !ts.isBefore(end)) continue; // lookback e fim: são de outra janela
            Map.Entry<LocalDateTime, String[]> prev = rows.lowerEntry(en.getKey());
            boolean prevKnown = prev != null && !prev.getKey().isBefore(en.getKey().minusMinutes(lookback));
            String[] p = en.getValue();
            ObjectNode payload = mapper.createObjectNode();
            payload.put("codestacao", p[0]);
            payload.put("datahora", p[6]);
            payload.put("sensor", p[7]);
            payload.put("valor", p[8]);
            payload.put("qualificacao", p[9]);
            payload.put("linha", String.join(";", p));
            if (prevKnown) payload.put("datahoraAnterior", prev.getValue()[6]);
            else payload.putNull("datahoraAnterior");
            payload.put("lookbackMin", lookback);
            out.add(new RawRecord(SOURCE, stationId, collectedAt, p[6], TimestampNormalizer.normalize(p[6]),
                    declaredTz(), payload));
        }
        return out;
    }

    @Override
    public List<DraftValue> extract(RawRecord record) {
        JsonNode p = record.payload();
        Double v = number(p.path("valor").asText(""));
        if (v == null) return List.of();
        LocalDateTime ts = LocalDateTime.parse(p.path("datahora").asText(), DATAHORA);
        String prev = p.path("datahoraAnterior").asText(null);
        Duration window = prev == null || prev.isBlank() || "null".equals(prev)
                ? Duration.ofMinutes(p.path("lookbackMin").asInt(cfg().getLookbackMinutes()) + 1L)
                : Duration.between(LocalDateTime.parse(prev, DATAHORA), ts);
        String q = p.path("qualificacao").asText(null);
        return List.of(new DraftValue(VARIABLE, "EVENT", v, "mm", q == null || q.isBlank() ? null : q, window));
    }

    // ------------------------------------------------------------------------------------------------ estações

    /** Estações do cadastro dos municípios configurados que ficam a até o raio de seleção de algum ponto ativo. */
    List<StationInfo> selectedStations() {
        List<FloodPoint> pts = floodPoints.findByActiveTrue();
        List<StationInfo> out = new ArrayList<>();
        for (String ibge : cfg().getIbgeCodes()) {
            CemadenPedClient.Response r = client.registry(ibge);
            if (r.noData()) continue;
            for (StationInfo s : parseRegistry(r.body())) {
                if (s.lat() == null || s.lon() == null) continue;
                double d = pts.stream().mapToDouble(p -> 1000.0 * GeoUtils.calculateDistance(p.getLatitude(), p.getLongitude(),
                        s.lat(), s.lon())).min().orElse(Double.MAX_VALUE);
                if (d <= cfg().getStationSelectionRadiusM()) out.add(s);
            }
        }
        out.sort(Comparator.comparing(StationInfo::stationId));
        if (out.isEmpty()) throw new SourceUnavailableException("PED: nenhuma estação no cadastro perto dos pontos");
        return out;
    }

    List<StationInfo> parseRegistry(String json) {
        List<StationInfo> out = new ArrayList<>();
        try {
            for (JsonNode n : mapper.readTree(json)) {
                ObjectNode attrs = mapper.createObjectNode();
                for (String k : List.of("id_estacao", "id_tipoestacao", "tipoestacao_descricao", "data_instalacao",
                        "dh_inicio_inativo", "dh_ultima_remessa", "codibge", "uf", "rede_sigla")) {
                    if (n.hasNonNull(k)) attrs.set(k, n.get(k));
                }
                attrs.put("origem", "PED /pcds-cadastro/dados-cadastrais");
                out.add(new StationInfo(SOURCE, n.path("codestacao").asText(), n.path("nome").asText(null),
                        n.hasNonNull("latitude") ? n.get("latitude").asDouble() : null,
                        n.hasNonNull("longitude") ? n.get("longitude").asDouble() : null,
                        n.path("cidade").asText(null), attrs));
            }
        } catch (Exception e) {
            throw new SourceUnavailableException("PED: cadastro ilegível", e);
        }
        return out;
    }

    /** "2014-10-23 13:48:33.386" (UTC, como todo dado do Cemaden) → instante; nulo se ausente. */
    static Instant instant(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return LocalDateTime.parse(text.substring(0, 19).replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Double number(String text) {
        try {
            return text == null || text.isBlank() ? null : Double.valueOf(text.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
