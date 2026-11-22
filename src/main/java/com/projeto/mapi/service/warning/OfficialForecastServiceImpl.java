package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.OfficialForecastDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawInsert;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawVersion;
import com.projeto.mapi.repository.warning.OfficialForecastRepository;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.warning.InmetForecastParser.ParsedForecast;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Coleta e leitura da previsão oficial do INMET por município. Bruto antes de extrair: o corpo inteiro vai para
 * {@code raw_observation} com SHA-256 (só quando muda) antes do parse; cada versão vista é uma linha em
 * {@code official_forecast}.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OfficialForecastServiceImpl implements OfficialForecastService {

    static final String PARSER_VERSION = "inmet-previsao/1";
    static final String RULE_VERSION = "horizontes-a/1";
    /** Chave fixa do bruto por município: a versão é decidida pelo hash do corpo. */
    static final String RAW_TS_KEY = "previsao";
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    private final InmetWarningClient client;
    private final OfficialForecastRepository forecasts;
    private final FloodPointRepository floodPoints;
    private final IngestionRunRepository runs;
    private final RawObservationRepository raws;
    private final AppProperties appProps;
    private final IngestionProperties ingestionProps;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Override
    public int syncAll() {
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID runId = UUID.randomUUID();
        Set<String> targets = targetGeocodes();
        ObjectNode params = mapper.createObjectNode();
        params.putPOJO("targetGeocodes", targets);
        runs.create(runId, InmetForecastParser.SOURCE, IngestionKind.LIVE.name(), IngestionMode.REFETCH.name(), null, null,
                ingestionProps.getCodeVersion(), PARSER_VERSION, RULE_VERSION, startedAt, null, params);
        RunCounters c = new RunCounters();
        List<String> errors = new ArrayList<>();
        int inserted = 0;
        boolean first = true;
        for (String ibge : targets) {
            if (!first) pause();
            first = false;
            try {
                inserted += syncOne(runId, ibge, c);
            } catch (RuntimeException e) {
                errors.add(ibge + ": " + e.getMessage());
                log.warn("Falha na previsão do INMET para {}: {}", ibge, e.toString());
            }
        }
        String status = errors.isEmpty() ? "OK" : errors.size() < targets.size() ? "PARTIAL" : "FAILED";
        runs.finish(runId, status, clock.instant().truncatedTo(ChronoUnit.MICROS), c,
                errors.isEmpty() ? null : String.join("; ", errors), params);
        return inserted;
    }

    private int syncOne(UUID runId, String ibge, RunCounters c) {
        String body = client.fetchForecastJson(ibge);
        Instant seenAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String rawHash = Hashing.sha256(body);
        JsonNode payload;
        try {
            payload = mapper.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("Previsão do INMET ilegível para " + ibge + ": " + e.getMessage(), e);
        }
        // 1. bruto, antes de qualquer extração
        RawRecord rec = new RawRecord(InmetForecastParser.SOURCE, ibge, seenAt, RAW_TS_KEY, RAW_TS_KEY,
                InmetForecastParser.SOURCE_TZ, payload);
        Map<OriginKey, RawVersion> latest = raws.latestVersions(InmetForecastParser.SOURCE, List.of(rec.key()));
        RawVersion prev = latest.get(rec.key());
        String contentHash = Hashing.contentHash(payload);
        List<RawInsert> rawInserts = new ArrayList<>();
        if (prev != null && prev.contentHash().equals(contentHash)) {
            c.addRawUnchanged(1);
        } else {
            rawInserts.add(new RawInsert(UUID.randomUUID(), rec, contentHash));
            c.addRawInserted(1);
        }
        // 2. extração
        List<ParsedForecast> entries = InmetForecastParser.parse(body, ibge, mapper);
        c.addFetched(entries.size());
        c.addValid(entries.size());
        Integer n = tx.execute(s -> {
            raws.insertAll(runId, seenAt, rawInserts);
            return forecasts.upsertAll(InmetForecastParser.SOURCE, runId, seenAt, rawHash, entries);
        });
        int inserted = n == null ? 0 : n;
        c.addObsInserted(inserted);
        c.addObsUnchanged(entries.size() - inserted);
        return inserted;
    }

    private void pause() {
        try {
            Thread.sleep(Math.max(0, appProps.getInmet().getForecastPauseMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    Set<String> targetGeocodes() {
        Set<String> out = new LinkedHashSet<>(appProps.getInmet().getTargetGeocodes());
        for (FloodPoint fp : floodPoints.findByActiveTrue()) {
            InmetWarningParser.resolveIbge(fp.getMunicipality()).ifPresent(out::add);
        }
        return out;
    }

    @Override
    public OfficialForecastDTO current(String municipio) {
        Optional<String> ibge = InmetWarningParser.resolveIbge(municipio);
        if (ibge.isEmpty()) {
            return empty(null, null, null, "MUNICIPIO_DESCONHECIDO",
                    "Município não reconhecido. Use o código IBGE de 7 dígitos ou um município da Região Metropolitana do Recife.");
        }
        Instant now = clock.instant();
        Instant lastRun = forecasts.lastSuccessfulRun(InmetForecastParser.SOURCE);
        LocalDate today = now.atZone(RECIFE).toLocalDate();
        List<OfficialForecastRepository.Row> rows = forecasts.current(InmetForecastParser.SOURCE, ibge.get(), today);
        if (rows.isEmpty()) {
            return empty(ibge.get(), null, lastRun, "SEM_PREVISAO",
                    "Ainda não há previsão do INMET gravada para este município. Isto não quer dizer tempo bom.");
        }
        Instant newestSeen = rows.stream().map(OfficialForecastRepository.Row::lastSeenAt).max(Instant::compareTo).orElse(null);
        long age = Duration.between(newestSeen, now).toMinutes();
        boolean stale = age > OfficialForecastDTO.MAX_AGE_MINUTES;
        Map<LocalDate, List<OfficialForecastDTO.Period>> byDay = new LinkedHashMap<>();
        for (OfficialForecastRepository.Row r : rows) {
            byDay.computeIfAbsent(r.forecastDate(), k -> new ArrayList<>()).add(new OfficialForecastDTO.Period(
                    r.period(), r.summary(), r.iconCode(), r.tempMax(), r.tempMin(), r.humidityMax(), r.humidityMin(),
                    r.windDir(), r.windIntensity(), r.firstSeenAt(), r.lastSeenAt()));
        }
        List<OfficialForecastDTO.Day> days = byDay.entrySet().stream()
                .map(e -> new OfficialForecastDTO.Day(e.getKey(), e.getValue())).toList();
        return new OfficialForecastDTO(OfficialForecastDTO.SOURCE, OfficialForecastDTO.SOURCE_LABEL,
                OfficialForecastDTO.ATTRIBUTION, OfficialForecastDTO.NOTE, ibge.get(), rows.get(0).municipality(),
                lastRun, age, stale, stale ? "FONTE_VELHA" : null,
                stale ? "A previsão do INMET mais nova foi vista há mais de 12 h. A coleta pode estar parada." : null,
                days);
    }

    private OfficialForecastDTO empty(String ibge, String municipality, Instant lastRun, String reason, String text) {
        return new OfficialForecastDTO(OfficialForecastDTO.SOURCE, OfficialForecastDTO.SOURCE_LABEL,
                OfficialForecastDTO.ATTRIBUTION, OfficialForecastDTO.NOTE, ibge, municipality, lastRun, null, true,
                reason, text, List.of());
    }
}
