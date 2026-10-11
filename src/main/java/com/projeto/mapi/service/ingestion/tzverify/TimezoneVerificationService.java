package com.projeto.mapi.service.ingestion.tzverify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.service.ingestion.adapter.OpenMeteoArchiveClient;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Criteria;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Decision;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Evaluation;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Reading;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Executa a verificação de fuso (F2-DADOS-07) sobre o bruto do staging: teste A (correlação de chuva contra o
 * Open-Meteo, para quem tem histórico) e teste B (relógio, para quem só existe ao vivo). Os limiares vêm de
 * {@link Criteria#preRegistered()} e {@link WallClockCheck.Criteria#preRegistered()}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimezoneVerificationService {

    public static final int BOOTSTRAP = 1000;
    public static final long SEED = 20261010L;

    public record StationReport(String source, String stationId, String name, Double lat, Double lon, int nReadings,
                                String firstLabel, String lastLabel, String referenceKind, Evaluation evaluation,
                                Decision decision) {}

    public record SourceReport(String source, List<StationReport> stations, Decision decision,
                               WallClockCheck.Result wallClock) {}

    private final JdbcTemplate jdbc;
    private final OpenMeteoArchiveClient archive;
    private final ExternalRestClientFactory http;
    private final IngestionProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    // ---- teste A com histórico (archive) -------------------------------------------------------

    /** Teste A para todas as estações da fonte que tenham chuva no bruto, contra o Open-Meteo archive. */
    public SourceReport verifyWithHistory(String source, String rainKey, int minReadings) {
        List<StationReport> reports = new ArrayList<>();
        for (StationRow st : stationsWithRain(source, rainKey)) {
            List<Reading> readings = readings(source, st.id(), rainKey);
            if (readings.isEmpty()) continue;
            long lo = readings.stream().mapToLong(Reading::labelEpochSeconds).min().orElseThrow();
            long hi = readings.stream().mapToLong(Reading::labelEpochSeconds).max().orElseThrow();
            Map<Long, Double> ref = archiveReference(st.lat(), st.lon(), lo, hi);
            Evaluation ev = TzCorrelation.evaluate(readings, ref, minReadings, BOOTSTRAP, SEED);
            Decision d = TzCorrelation.decide(ev, Criteria.preRegistered());
            reports.add(new StationReport(source, st.id(), st.name(), st.lat(), st.lon(), readings.size(),
                    iso(lo), iso(hi), "OPEN_METEO_ARCHIVE", ev, d));
            log.info("[{}] {} {} readings={} best={} decision={}", source, st.id(), st.name(), readings.size(),
                    ev.bestOffset(), d);
        }
        Decision src = TzCorrelation.decideSource(reports.stream().map(StationReport::decision).toList(), 2);
        return new SourceReport(source, reports, src, null);
    }

    // ---- fontes só ao vivo ---------------------------------------------------------------------

    /**
     * Teste B (decisivo) sobre as execuções LIVE e teste A exploratório (últimas horas, Open-Meteo forecast com
     * past_days) nas estações com mais chuva na janela. Só o teste B decide.
     */
    public SourceReport verifyLiveOnly(String source, String rainKey, int exploratoryStations) {
        List<WallClockCheck.Sample> samples = jdbc.query("""
                SELECT r.station_id, r.source_ts_norm, r.collected_at
                FROM raw_observation r JOIN ingestion_run i ON i.id = r.run_id
                WHERE r.source = ? AND i.kind = 'LIVE'
                """, (rs, n) -> {
            Long label = labelSeconds(rs.getString(2));
            return label == null ? null : new WallClockCheck.Sample(rs.getString(1),
                    rs.getTimestamp(3).toInstant(), label);
        }, source).stream().filter(java.util.Objects::nonNull).toList();
        WallClockCheck.Result wall = WallClockCheck.evaluate(samples, WallClockCheck.Criteria.preRegistered());
        Decision decision = new Decision(wall.verdict(), wall.offsetHours(), wall.reason());

        // exploratório: estações com mais chuva acumulada no bruto da janela
        List<StationReport> reports = new ArrayList<>();
        List<StationRow> rainy = stationsWithRain(source, rainKey).stream()
                .sorted(Comparator.comparingDouble((StationRow s) -> -totalRain(source, s.id(), rainKey)))
                .limit(exploratoryStations).toList();
        for (StationRow st : rainy) {
            List<Reading> readings = readings(source, st.id(), rainKey);
            if (readings.isEmpty()) continue;
            long lo = readings.stream().mapToLong(Reading::labelEpochSeconds).min().orElseThrow();
            long hi = readings.stream().mapToLong(Reading::labelEpochSeconds).max().orElseThrow();
            Map<Long, Double> ref = recentReference(st.lat(), st.lon());
            Evaluation ev = TzCorrelation.evaluate(readings, ref, 1, BOOTSTRAP, SEED);
            reports.add(new StationReport(source, st.id(), st.name(), st.lat(), st.lon(), readings.size(),
                    iso(lo), iso(hi), "OPEN_METEO_FORECAST_PAST_DAYS", ev, TzCorrelation.decide(ev, Criteria.preRegistered())));
        }
        return new SourceReport(source, reports, decision, wall);
    }

    // ---- acesso a dados ------------------------------------------------------------------------

    private record StationRow(String id, String name, Double lat, Double lon) {}

    private List<StationRow> stationsWithRain(String source, String rainKey) {
        return jdbc.query("""
                SELECT s.station_id, s.name, s.lat, s.lon FROM station s
                WHERE s.source = ? AND s.lat IS NOT NULL AND s.lon IS NOT NULL
                  AND EXISTS (SELECT 1 FROM raw_observation r WHERE r.source = s.source AND r.station_id = s.station_id
                              AND r.payload ->> ? IS NOT NULL LIMIT 1)
                ORDER BY s.station_id
                """, (rs, n) -> new StationRow(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4)),
                source, rainKey);
    }

    private double totalRain(String source, String station, String rainKey) {
        return readings(source, station, rainKey).stream().mapToDouble(Reading::mm).sum();
    }

    /** Versão mais recente de cada registro de origem (estação x rótulo); lê só a chuva. */
    List<Reading> readings(String source, String station, String rainKey) {
        List<Reading> out = new ArrayList<>();
        jdbc.query("""
                SELECT DISTINCT ON (source_ts_norm) source_ts_norm, payload ->> ?
                FROM raw_observation WHERE source = ? AND station_id = ?
                ORDER BY source_ts_norm, ingested_at DESC, seq DESC
                """, rs -> {
            Long label = labelSeconds(rs.getString(1));
            String v = rs.getString(2);
            if (label == null || v == null) return;
            try {
                double mm = Double.parseDouble(v.trim().replace(',', '.'));
                if (mm >= 0 && mm < 1000) out.add(new Reading(label, mm));
            } catch (NumberFormatException ignored) {
                // texto não numérico = lacuna
            }
        }, rainKey, source, station);
        return out;
    }

    static Long labelSeconds(String norm) {
        if (norm == null || norm.length() < 16) return null;
        try {
            return LocalDateTime.parse(norm.length() > 19 ? norm.substring(0, 19) : norm).toEpochSecond(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String iso(long epochSeconds) {
        return LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC).toString();
    }

    /** Chuva horária do archive (chave = hora do fim do intervalo), em blocos de até 366 dias. */
    Map<Long, Double> archiveReference(double lat, double lon, long loLabel, long hiLabel) {
        LocalDate first = LocalDateTime.ofEpochSecond(loLabel, 0, ZoneOffset.UTC).toLocalDate().minusDays(1);
        LocalDate last = LocalDateTime.ofEpochSecond(hiLabel, 0, ZoneOffset.UTC).toLocalDate().plusDays(1);
        LocalDate lastAvailable = clock.instant().atZone(ZoneOffset.UTC).toLocalDate().minusDays(props.getOpenMeteoArchiveLagDays());
        if (last.isAfter(lastAvailable)) last = lastAvailable;
        Map<Long, Double> ref = new TreeMap<>();
        for (LocalDate s = first; !s.isAfter(last); s = s.plusDays(366)) {
            LocalDate e = s.plusDays(365).isAfter(last) ? last : s.plusDays(365);
            OpenMeteoArchiveClient.Response resp = archive.fetch(lat, lon, s, e);
            parseHourly(resp.body(), ref);
        }
        return ref;
    }

    /** Últimas horas (past_days = 3) da previsão/análise do Open-Meteo, em UTC. */
    Map<Long, Double> recentReference(double lat, double lon) {
        String url = String.format(java.util.Locale.ROOT,
                "%s/forecast?latitude=%s&longitude=%s&hourly=precipitation&past_days=3&forecast_days=1&timezone=GMT",
                props.getOpenMeteoForecastUrl(), lat, lon);
        String body = http.client("open-meteo-archive", props.getOpenMeteoArchiveUrl(), Map.of())
                .get().uri(URI.create(url)).retrieve().body(String.class);
        Map<Long, Double> ref = new TreeMap<>();
        parseHourly(body, ref);
        return ref;
    }

    private void parseHourly(String body, Map<Long, Double> into) {
        try {
            JsonNode root = mapper.readTree(body);
            if (root.path("utc_offset_seconds").asInt(-1) != 0) {
                throw new IllegalStateException("Open-Meteo com utc_offset_seconds != 0: " + root.path("utc_offset_seconds"));
            }
            JsonNode times = root.path("hourly").path("time");
            JsonNode prec = root.path("hourly").path("precipitation");
            for (int i = 0; i < times.size(); i++) {
                if (!prec.get(i).isNumber()) continue;
                long sec = LocalDateTime.parse(times.get(i).asText()).toEpochSecond(ZoneOffset.UTC);
                into.put(sec / 3600, prec.get(i).asDouble());
            }
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Resposta do Open-Meteo ilegível", e);
        }
    }
}
