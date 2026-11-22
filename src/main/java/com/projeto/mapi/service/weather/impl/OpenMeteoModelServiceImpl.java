package com.projeto.mapi.service.weather.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.analise.OpenMeteoModelRepository;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawInsert;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.weather.OpenMeteoModelClient;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.Batch;
import com.projeto.mapi.service.weather.OpenMeteoModelClient.RunMeta;
import com.projeto.mapi.service.weather.OpenMeteoModelService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Open-Meteo por modelo (V42). Emissão separada da validade: a rodada ao vivo leva o instante de publicação do meta.json;
 * o histórico leva o limite conservador {@code hora - 24N h + 8 h}. Bruto com SHA-256 antes de extrair. Uma requisição
 * por vez, com pausa. Falha de um modelo não impede os outros (ex.: ICON Global, descontinuado em 30/11/2026).
 */
@Service
@Slf4j
public class OpenMeteoModelServiceImpl implements OpenMeteoModelService {

    static final String SOURCE = "OPEN_METEO_FORECAST";
    static final String PARSER_VERSION = "open-meteo-modelos/1";
    static final String RULE_VERSION = "horizontes-b/1";
    static final int LIVE_HOURS = 48;
    /** Maior atraso de publicação visto no meta.json em 22/11/2026: 7h05 (ECMWF 0,25°). Margem: 8 h. */
    static final Duration PUBLICATION_MARGIN = Duration.ofHours(8);
    static final List<Integer> LEADS = List.of(1, 2, 3);
    /** Único dos modelos com CAPE no histórico de rodadas (conferido em 22/11/2026). */
    static final String CAPE_HISTORY_MODEL = "best_match";

    private final OpenMeteoModelClient client;
    private final OpenMeteoModelRepository repo;
    private final FloodPointRepository points;
    private final IngestionRunRepository runs;
    private final RawObservationRepository raws;
    private final IngestionProperties ingestionProps;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final List<String> liveModels;
    private final List<String> historyModels;
    private final long pauseMs;
    private final AtomicBoolean backfillRunning = new AtomicBoolean(false);

    public OpenMeteoModelServiceImpl(OpenMeteoModelClient client, OpenMeteoModelRepository repo, FloodPointRepository points,
                                     IngestionRunRepository runs, RawObservationRepository raws,
                                     IngestionProperties ingestionProps, ObjectMapper mapper, Clock clock, TransactionTemplate tx,
                                     @Value("${app.open-meteo-models.live:ecmwf_ifs,ecmwf_ifs025,dwd_icon_global_native,best_match}") List<String> liveModels,
                                     @Value("${app.open-meteo-models.history:ecmwf_ifs025,icon_seamless,best_match}") List<String> historyModels,
                                     @Value("${app.open-meteo-models.pause-ms:2000}") long pauseMs) {
        this.client = client;
        this.repo = repo;
        this.points = points;
        this.runs = runs;
        this.raws = raws;
        this.ingestionProps = ingestionProps;
        this.mapper = mapper;
        this.clock = clock;
        this.tx = tx;
        this.liveModels = liveModels;
        this.historyModels = historyModels;
        this.pauseMs = pauseMs;
    }

    @Override
    public int collectLive() {
        UUID runId = UUID.randomUUID();
        Instant started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ObjectNode params = mapper.createObjectNode();
        params.put("product", "run");
        params.putPOJO("models", liveModels);
        runs.create(runId, SOURCE, IngestionKind.LIVE.name(), IngestionMode.REFETCH.name(), null, null,
                ingestionProps.getCodeVersion(), PARSER_VERSION, RULE_VERSION, started, null, params);
        RunCounters c = new RunCounters();
        List<String> errors = new ArrayList<>();
        int inserted = 0;
        for (String model : liveModels) {
            try {
                inserted += collectModel(runId, model, c);
            } catch (RuntimeException e) {
                errors.add(model + ": " + e.getMessage());
                log.warn("Open-Meteo, modelo {}: {}", model, e.toString());
            }
        }
        String status = errors.isEmpty() ? "OK" : errors.size() < liveModels.size() ? "PARTIAL" : "FAILED";
        runs.finish(runId, status, clock.instant().truncatedTo(ChronoUnit.MICROS), c,
                errors.isEmpty() ? null : String.join("; ", errors), params);
        return inserted;
    }

    private int collectModel(UUID runId, String model, RunCounters c) {
        Optional<RunMeta> meta = client.runMeta(model);
        Instant fetchedAt = clock.instant();
        if (meta.isPresent()) {
            Instant last = repo.lastRunInit(model);
            if (last != null && !meta.get().initUtc().isAfter(last)) return 0;   // nenhuma rodada nova
        }
        int n = 0;
        for (FloodPoint p : points.findByActiveTrue()) {
            pause();
            Batch b = client.liveRun(p.getLatitude(), p.getLongitude(), model);
            Instant now = clock.instant();
            Instant issued = meta.map(RunMeta::availableUtc).orElse(fetchedAt);
            Instant init = meta.map(RunMeta::initUtc).orElse(null);
            List<OpenMeteoModelClient.Hour> ahead = b.hours().stream()
                    .filter(h -> h.hourEndUtc().isAfter(now) && !h.hourEndUtc().isAfter(now.plus(Duration.ofHours(LIVE_HOURS))))
                    .toList();
            c.addFetched(b.hours().size());
            Integer k = tx.execute(s -> {
                raw(runId, p.getSlug() + "|" + model + "|run", init == null ? issued : init, b, c);
                return repo.insertAll(p.getId(), model, "run", ahead, h -> issued, init, b.gridLat(), b.gridLon(), b.rawHash());
            });
            n += k == null ? 0 : k;
        }
        c.addObsInserted(n);
        return n;
    }

    @Override
    @Async("taskExecutor")
    public void backfillAsync(LocalDate from) {
        if (!backfillRunning.compareAndSet(false, true)) {
            log.info("Carga do histórico do Open-Meteo já em andamento");
            return;
        }
        try {
            backfill(from);
        } finally {
            backfillRunning.set(false);
        }
    }

    void backfill(LocalDate from) {
        UUID runId = UUID.randomUUID();
        Instant started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ObjectNode params = mapper.createObjectNode();
        params.put("from", from.toString());
        params.putPOJO("models", historyModels);
        runs.create(runId, SOURCE, IngestionKind.BACKFILL.name(), IngestionMode.REFETCH.name(), null, null,
                ingestionProps.getCodeVersion(), PARSER_VERSION, RULE_VERSION, started, null, params);
        RunCounters c = new RunCounters();
        List<String> errors = new ArrayList<>();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        for (FloodPoint p : points.findByActiveTrue()) {
            for (String model : historyModels) {
                for (LocalDate start = from; !start.isAfter(today); start = start.plusYears(1).withDayOfYear(1)) {
                    LocalDate end = start.plusYears(1).withDayOfYear(1).minusDays(1);
                    if (end.isAfter(today)) end = today;
                    Instant a = start.atStartOfDay(ZoneOffset.UTC).toInstant();
                    Instant z = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                    boolean done = LEADS.stream().allMatch(n -> repo.hasHistory(p.getId(), model, "previous_day" + n, a, z));
                    if (done && end.isBefore(today)) continue;               // ano fechado e já carregado
                    try {
                        pause();
                        Map<Integer, Batch> byLead = client.previousRuns(p.getLatitude(), p.getLongitude(), model, start, end,
                                LEADS, CAPE_HISTORY_MODEL.equals(model));
                        Batch any = byLead.values().iterator().next();
                        LocalDate s0 = start;
                        tx.execute(s -> {
                            raw(runId, p.getSlug() + "|" + model + "|previous", s0.atStartOfDay(ZoneOffset.UTC).toInstant(), any, c);
                            byLead.forEach((n, b) -> c.addObsInserted(repo.insertAll(p.getId(), model, "previous_day" + n,
                                    b.hours(), h -> h.hourEndUtc().minus(Duration.ofHours(24L * n)).plus(PUBLICATION_MARGIN),
                                    null, b.gridLat(), b.gridLon(), b.rawHash())));
                            return null;
                        });
                    } catch (RuntimeException e) {
                        errors.add(p.getSlug() + "/" + model + "/" + start.getYear() + ": " + e.getMessage());
                        log.warn("Histórico do Open-Meteo {} {} {}: {}", p.getSlug(), model, start.getYear(), e.toString());
                    }
                }
            }
        }
        runs.finish(runId, errors.isEmpty() ? "OK" : "PARTIAL", clock.instant().truncatedTo(ChronoUnit.MICROS), c,
                errors.isEmpty() ? null : String.join("; ", errors), params);
    }

    /** Bruto antes de extrair: corpo inteiro com SHA-256 (o mesmo corpo não se repete). */
    private void raw(UUID runId, String stationKey, Instant ts, Batch b, RunCounters c) {
        try {
            RawRecord rec = new RawRecord(SOURCE, stationKey, clock.instant().truncatedTo(ChronoUnit.MICROS), ts.toString(),
                    ts.toString(), "UTC", mapper.readTree(b.rawBody()));
            var prev = raws.latestVersions(SOURCE, List.of(rec.key())).get(rec.key());
            String h = Hashing.contentHash(rec.payload());
            if (prev != null && prev.contentHash().equals(h)) {
                c.addRawUnchanged(1);
                return;
            }
            raws.insertAll(runId, rec.collectedAt(), List.of(new RawInsert(UUID.randomUUID(), rec, h)));
            c.addRawInserted(1);
        } catch (Exception e) {
            throw new IllegalStateException("Bruto do Open-Meteo não gravado: " + e.getMessage(), e);
        }
    }

    private void pause() {
        try {
            Thread.sleep(Math.max(0, pauseMs));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public List<Map<String, Object>> summary() {
        return repo.summary().stream().map(repo::asMap).toList();
    }
}
