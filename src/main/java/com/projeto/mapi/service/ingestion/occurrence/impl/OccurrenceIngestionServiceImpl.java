package com.projeto.mapi.service.ingestion.occurrence.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.repository.ingestion.OccurrenceRepository;
import com.projeto.mapi.repository.ingestion.OccurrenceRepository.OccurrenceRow;
import com.projeto.mapi.repository.ingestion.OccurrenceRepository.Stored;
import com.projeto.mapi.repository.ingestion.QuarantineRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawInsert;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawVersion;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.ingestion.occurrence.CkanAddress;
import com.projeto.mapi.service.ingestion.occurrence.CkanRawRecord;
import com.projeto.mapi.service.ingestion.occurrence.LabelFilterConfig;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification.Status;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassifier;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Rótulo CKAN no pipeline (ADR 0006 seção 6). Ordem: run -> bruto (append-only, dedupe pelo hash da versão mais
 * recente) -> classificação (filtro de texto, DAY/HOUR) -> geocoding fora da transação -> flood_occurrence ->
 * occurrence_point_match com o raio gravado. NUNCA consulta chuva nem realinha horário.
 */
@Service
@Slf4j
public class OccurrenceIngestionServiceImpl implements OccurrenceIngestionService {

    static final String LABEL_FILTER_NAME = "cobrade-alagamento";
    static final String TZ = "America/Recife";
    static final String PARSER_VERSION = "ckan-recife/1";
    static final String GEOCODE_METHOD = "GEOCODING_SERVICE";
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    private record Item(CkanRawRecord record, RawRecord raw, String hash, OccurrenceClassification cls) {}

    private final IngestionRunRepository runs;
    private final RawObservationRepository raws;
    private final OccurrenceRepository occurrences;
    private final QuarantineRepository quarantine;
    private final FloodPointRepository floodPoints;
    private final GeocodingService geocoding;
    private final IngestionProperties props;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;

    public OccurrenceIngestionServiceImpl(IngestionRunRepository runs, RawObservationRepository raws,
                                          OccurrenceRepository occurrences, QuarantineRepository quarantine,
                                          FloodPointRepository floodPoints, GeocodingService geocoding,
                                          IngestionProperties props, Clock clock, ObjectMapper mapper,
                                          PlatformTransactionManager txManager) {
        this.runs = runs;
        this.raws = raws;
        this.occurrences = occurrences;
        this.quarantine = quarantine;
        this.floodPoints = floodPoints;
        this.geocoding = geocoding;
        this.props = props;
        this.clock = clock;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public IngestionRunView ingest(String packageId, String resourceId, List<CkanRawRecord> records) {
        LabelFilterConfig filter = occurrences.labelFilter(LABEL_FILTER_NAME).orElseThrow(
                () -> new IllegalStateException("label_filter '" + LABEL_FILTER_NAME + "' ausente (V6)"));
        UUID runId = UUID.randomUUID();
        Instant started = clock.instant().truncatedTo(ChronoUnit.MICROS);
        ObjectNode params = mapper.createObjectNode();
        params.put("source", SOURCE);
        params.put("packageId", packageId);
        params.put("resourceId", resourceId);
        params.put("labelFilterId", filter.id());
        params.put("labelFilterTexts", String.join("|", filter.acceptedTexts()));
        runs.create(runId, SOURCE, "BACKFILL", "REFETCH", null, null, props.getCodeVersion(), PARSER_VERSION,
                props.getRuleVersion(), started, null, params);

        RunCounters c = new RunCounters();
        String status = "OK";
        String error = null;
        try {
            // 1. classificação (pura) e dedupe do lote por registro (vence o último)
            Map<String, Item> byRecord = new LinkedHashMap<>();
            for (CkanRawRecord r : records) {
                byRecord.put(r.recordId(), classify(resourceId, r, filter, started));
            }
            c.addFetched(byRecord.size());
            c.addBatchDeduped(records.size() - byRecord.size());

            // 2. geocoding fora da transação, só para ocorrências novas ou cujo bruto mudou
            Map<String, Stored> stored = occurrences.stored(resourceId, new ArrayList<>(byRecord.keySet()));
            Map<String, double[]> coords = new LinkedHashMap<>();
            int geocodeFailed = 0;
            for (Item it : byRecord.values()) {
                if (it.cls().status() != Status.ACCEPTED) continue;
                Stored s = stored.get(it.record().recordId());
                if (s != null && s.rawHash().equals(it.hash())) continue;
                Optional<double[]> g = geocode(it.cls());
                if (g.isPresent()) coords.put(it.record().recordId(), g.get());
                else geocodeFailed++;
            }
            params.put("geocodeFailed", geocodeFailed);

            // 3. gravação em uma transação
            int[] written = {0};
            tx.executeWithoutResult(s -> written[0] = persist(runId, packageId, resourceId, byRecord, coords, c));
            params.put("excludedByFilter", byRecord.values().stream()
                    .filter(i -> i.cls().status() == Status.EXCLUDED_BY_FILTER).count());
            params.put("occurrencesWritten", written[0]);

            // 4. casamento com o raio vigente (gravado em linkage_config)
            long cfg = rematch(props.getOccurrenceRadiusM());
            params.put("linkageConfigId", cfg);
            params.put("radiusM", props.getOccurrenceRadiusM());
        } catch (RuntimeException e) {
            status = "FAILED";
            error = e.toString();
            log.error("Run CKAN {} falhou", runId, e);
        }
        runs.finish(runId, status, clock.instant().truncatedTo(ChronoUnit.MICROS), c, error, params);
        return runs.findById(runId).orElseThrow();
    }

    private Item classify(String resourceId, CkanRawRecord r, LabelFilterConfig filter, Instant collectedAt) {
        JsonNode payload = mapper.valueToTree(r.fields());
        String dataText = textOf(r.fields(), "Data");
        String norm = TimestampNormalizer.normalize(dataText);
        RawRecord raw = new RawRecord(SOURCE, resourceId + "#" + r.recordId(), collectedAt,
                dataText.length() > 80 ? dataText.substring(0, 80) : dataText, norm, TZ, payload);
        return new Item(r, raw, Hashing.contentHash(payload), OccurrenceClassifier.classify(r.fields(), filter, TZ));
    }

    private static String textOf(Map<String, Object> fields, String key) {
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey()) && e.getValue() != null) return String.valueOf(e.getValue()).trim();
        }
        return "";
    }

    private Optional<double[]> geocode(OccurrenceClassification cls) {
        String clean = CkanAddress.sanitize(cls.address());
        if (clean.isBlank()) return Optional.empty();
        try {
            return geocoding.geocode(clean, cls.neighborhood(), "Recife");
        } catch (RuntimeException e) {
            log.warn("Geocoding falhou para '{}': {}", clean, e.getMessage());
            return Optional.empty();
        }
    }

    private int persist(UUID runId, String packageId, String resourceId, Map<String, Item> byRecord,
                        Map<String, double[]> coords, RunCounters c) {
        runs.lockSource(SOURCE);
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Map<OriginKey, RawVersion> latest = raws.latestVersions(SOURCE,
                byRecord.values().stream().map(i -> i.raw().key()).toList());
        List<RawInsert> inserts = new ArrayList<>();
        Map<String, UUID> rawIds = new LinkedHashMap<>();
        Map<String, Instant> rawAt = new LinkedHashMap<>();
        for (Item it : byRecord.values()) {
            RawVersion prev = latest.get(it.raw().key());
            String id = it.record().recordId();
            if (prev != null && prev.contentHash().equals(it.hash())) {
                c.addRawUnchanged(1);
                rawIds.put(id, prev.id());
                rawAt.put(id, prev.ingestedAt());
            } else {
                UUID rid = UUID.randomUUID();
                inserts.add(new RawInsert(rid, it.raw(), it.hash()));
                rawIds.put(id, rid);
                rawAt.put(id, now);
            }
        }
        raws.insertAll(runId, now, inserts);
        c.addRawInserted(inserts.size());

        List<QuarantineRepository.Entry> entries = new ArrayList<>();
        int written = 0;
        for (Item it : byRecord.values()) {
            String id = it.record().recordId();
            OccurrenceClassification cls = it.cls();
            if (cls.status() == Status.TS_UNPARSEABLE) {
                entries.add(new QuarantineRepository.Entry(rawIds.get(id), rawAt.get(id), "TS", "TS_UNPARSEABLE",
                        Map.of("recordId", id, "detail", String.valueOf(cls.detail()))));
                continue;
            }
            if (cls.status() != Status.ACCEPTED) continue;
            double[] g = coords.get(id);
            written += occurrences.upsert(new OccurrenceRow(packageId, resourceId, id, it.hash(), runId,
                    cls.address(), cls.occurredFrom(), cls.occurredTo(), cls.precision(), null,
                    "TEXT_MATCH:" + cls.matchedText(), g == null ? null : g[0], g == null ? null : g[1],
                    g == null ? "NOT_GEOCODED" : GEOCODE_METHOD));
        }
        quarantine.insertIfAbsent(runId, props.getRuleVersion(), now, entries);
        c.addQuarantined(entries.size());
        c.addValid(written);
        return written;
    }

    @Override
    public long rematch(double radiusM) {
        long cfg = occurrences.findOrCreateOccurrenceConfig(radiusM, "pipeline",
                "raio configurado em mapi.ingestion.occurrence-radius-m; [sem fonte] como critério");
        List<FloodPoint> points = floodPoints.findAll();
        List<OccurrenceRepository.GeocodedOccurrence> occ = occurrences.geocoded();
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        tx.executeWithoutResult(s -> {
            for (OccurrenceRepository.GeocodedOccurrence o : occ) {
                FloodPoint best = null;
                double bestD = Double.MAX_VALUE;
                for (FloodPoint p : points) {
                    double d = haversineM(o.lat(), o.lon(), p.getLatitude(), p.getLongitude());
                    if (d < bestD) {
                        bestD = d;
                        best = p;
                    }
                }
                if (best != null && bestD <= radiusM) {
                    occurrences.insertMatch(o.id(), best.getId(), bestD, radiusM, cfg, props.getRuleVersion(), now);
                }
            }
        });
        return cfg;
    }

    static double haversineM(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }
}
