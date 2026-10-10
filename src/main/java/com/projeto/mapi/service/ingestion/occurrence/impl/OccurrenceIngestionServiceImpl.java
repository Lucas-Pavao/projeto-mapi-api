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
import com.projeto.mapi.service.geocoding.GeocodeLevel;
import com.projeto.mapi.service.geocoding.GeocodeResult;
import com.projeto.mapi.service.geocoding.GeocodingService;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.ingestion.occurrence.CkanAddress;
import com.projeto.mapi.service.ingestion.occurrence.CkanColumnMapping;
import com.projeto.mapi.service.ingestion.occurrence.CkanRawRecord;
import com.projeto.mapi.service.ingestion.occurrence.LabelFilterConfig;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassification.Status;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClassifier;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import com.projeto.mapi.service.ingestion.validation.TsUnparseableException;
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
import java.util.Set;
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
    static final String PARSER_VERSION = "ckan-recife/2";
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
        return execute("BACKFILL", "REFETCH", packageId, resourceId, sink -> sink.accept(records, null));
    }

    /** Tamanho da página do REVALIDATE (registros do bruto por lote). */
    static final int REVALIDATE_PAGE = 5000;

    @Override
    public IngestionRunView revalidate(String packageId, String resourceId) {
        return execute("REPROCESS", "REVALIDATE", packageId, resourceId, sink -> {
            String after = "";
            String prefix = resourceId + "#";
            CkanColumnMapping mapping = null;
            while (true) {
                List<RawVersion> page = raws.latestByStationPrefix(SOURCE, prefix, after, REVALIDATE_PAGE);
                if (page.isEmpty()) break;
                List<CkanRawRecord> records = new ArrayList<>(page.size());
                for (RawVersion v : page) {
                    String stationId = v.record().stationId();
                    records.add(new CkanRawRecord(stationId.substring(prefix.length()), fieldsOf(v.record().payload())));
                }
                if (mapping == null) {
                    mapping = CkanColumnMapping.detect(records.stream().map(CkanRawRecord::fields).toList());
                }
                sink.accept(records, mapping);
                after = page.get(page.size() - 1).record().stationId();
            }
        });
    }

    private Map<String, Object> fieldsOf(JsonNode payload) {
        Map<String, Object> fields = new LinkedHashMap<>();
        payload.fields().forEachRemaining(e -> fields.put(e.getKey(),
                e.getValue().isNull() ? null : e.getValue().isNumber() ? e.getValue().numberValue() : e.getValue().asText()));
        return fields;
    }

    /** Alimentador de lotes: entrega (registros, mapeamento) ao sink; mapeamento nulo = detectar no lote. */
    private interface BatchFeeder {
        void feed(BatchSink sink);
    }

    private interface BatchSink {
        void accept(List<CkanRawRecord> records, CkanColumnMapping mapping);
    }

    private IngestionRunView execute(String kind, String mode, String packageId, String resourceId, BatchFeeder feeder) {
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
        runs.create(runId, SOURCE, kind, mode, null, null, props.getCodeVersion(), PARSER_VERSION,
                props.getRuleVersion(), started, null, params);

        RunCounters c = new RunCounters();
        String status = "OK";
        String error = null;
        long[] totals = new long[4]; // geocodeFailed, excludedByFilter, written, quarantineResolved
        String[] mappingName = {null};
        try {
            feeder.feed((records, given) -> {
                CkanColumnMapping mapping = given != null ? given
                        : CkanColumnMapping.detect(records.stream().map(CkanRawRecord::fields).toList());
                mappingName[0] = mapping.name();
                processBatch(runId, packageId, resourceId, records, mapping, filter, started, c, totals);
            });
            params.put("columnMapping", mappingName[0]);
            params.put("geocodeFailed", totals[0]);
            params.put("excludedByFilter", totals[1]);
            params.put("occurrencesWritten", totals[2]);
            params.put("quarantineResolved", totals[3]);

            // casamento com o raio vigente (gravado em linkage_config)
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

    private void processBatch(UUID runId, String packageId, String resourceId, List<CkanRawRecord> records,
                              CkanColumnMapping mapping, LabelFilterConfig filter, Instant started, RunCounters c,
                              long[] totals) {
        // 1. classificação (pura) e dedupe do lote por registro (vence o último)
        Map<String, Item> byRecord = new LinkedHashMap<>();
        for (CkanRawRecord r : records) {
            byRecord.put(r.recordId(), classify(resourceId, r, filter, started, mapping));
        }
        c.addFetched(byRecord.size());
        c.addBatchDeduped(records.size() - byRecord.size());

        // 2. geocoding fora da transação, só para ocorrências novas ou cujo bruto mudou
        Map<String, Stored> stored = occurrences.stored(resourceId, new ArrayList<>(byRecord.keySet()));
        Set<String> inOtherResources = occurrences.knownInOtherResources(resourceId, new ArrayList<>(byRecord.keySet()));
        Map<String, GeocodeResult> coords = new LinkedHashMap<>();
        for (Item it : byRecord.values()) {
            if (it.cls().status() != Status.ACCEPTED || inOtherResources.contains(identity(it))) continue;
            Stored s = stored.get(it.record().recordId());
            if (s != null && s.rawHash().equals(it.hash())) continue;
            Optional<GeocodeResult> g = geocode(it.cls());
            if (g.isPresent()) coords.put(it.record().recordId(), g.get());
            else totals[0]++;
        }
        totals[1] += byRecord.values().stream().filter(i -> i.cls().status() == Status.EXCLUDED_BY_FILTER).count();

        // 3. gravação em uma transação
        int[] written = {0};
        long[] resolved = {0};
        tx.executeWithoutResult(s -> written[0] = persist(runId, packageId, resourceId, byRecord, coords,
                inOtherResources, c, resolved));
        totals[2] += written[0];
        totals[3] += resolved[0];
    }

    /** Tamanho de raw_observation.source_ts_norm (V4). Acima disso o valor não é data: vai à quarentena. */
    static final int MAX_TS_NORM = 40;
    static final int MAX_TS_TEXT = 80;
    static final int MAX_NEIGHBORHOOD = 120;

    private static String clip(String s, int max) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    private Item classify(String resourceId, CkanRawRecord r, LabelFilterConfig filter, Instant collectedAt,
                          CkanColumnMapping mapping) {
        JsonNode payload = mapper.valueToTree(r.fields());
        // Identidade do bruto: o texto literal da coluna "Data" (estável, independe do mapeamento de colunas).
        String dataText = textOf(r.fields(), "Data");
        // Classificação: data/tipo/endereço/bairro vêm da visão canônica do mapeamento do recurso (F2-BACK-18).
        Map<String, Object> canonical = mapping.canonical(r.fields());
        String mappedDate = textOf(canonical, "Data");
        String norm = TimestampNormalizer.normalize(mappedDate);
        boolean parseable = norm.length() <= MAX_TS_NORM && parses(norm);
        // O payload guarda o texto original inteiro; as colunas de identidade/auditoria são aparadas para caber
        // (V4: source_ts_text varchar(80), source_ts_norm varchar(40)). A identidade do bruto já inclui o _id.
        String text = dataText.length() > MAX_TS_TEXT ? dataText.substring(0, MAX_TS_TEXT) : dataText;
        String rawNorm = TimestampNormalizer.normalize(dataText);
        String storedRawNorm = rawNorm.length() > MAX_TS_NORM ? rawNorm.substring(0, MAX_TS_NORM) : rawNorm;
        RawRecord raw = new RawRecord(SOURCE, resourceId + "#" + r.recordId(), collectedAt, text, storedRawNorm, TZ, payload);
        OccurrenceClassification cls = OccurrenceClassifier.classify(canonical, filter, TZ);
        if (!parseable && cls.status() != Status.TS_UNPARSEABLE) {
            // data ilegível nunca derruba o lote: quarentena TS_UNPARSEABLE, mesmo em registro fora do filtro
            cls = new OccurrenceClassification(Status.TS_UNPARSEABLE, cls.matchedText(), null, null, null,
                    cls.address(), cls.neighborhood(), "Data não interpretável (" + mappedDate.length() + " caracteres)");
        }
        return new Item(r, raw, Hashing.contentHash(payload), cls);
    }

    private static boolean parses(String norm) {
        try {
            TimestampNormalizer.toInstant(norm, TZ);
            return true;
        } catch (TsUnparseableException e) {
            return false;
        }
    }

    /** Identidade de origem da ocorrência entre recursos: _id do datastore + início do intervalo. */
    private static String identity(Item it) {
        return it.record().recordId() + "|" + it.cls().occurredFrom();
    }

    private static String textOf(Map<String, Object> fields, String key) {
        for (Map.Entry<String, Object> e : fields.entrySet()) {
            if (key.equalsIgnoreCase(e.getKey()) && e.getValue() != null) return String.valueOf(e.getValue()).trim();
        }
        return "";
    }

    private Optional<GeocodeResult> geocode(OccurrenceClassification cls) {
        String clean = CkanAddress.sanitize(cls.address());
        boolean hasNeighborhood = cls.neighborhood() != null && !cls.neighborhood().isBlank();
        // endereço ilegível mas bairro conhecido: geocodifica no nível do bairro (honesto no geocode_level)
        if (clean.isBlank() && !hasNeighborhood) return Optional.empty();
        try {
            return geocoding.geocodeWithLevel(clean, cls.neighborhood(), "Recife");
        } catch (RuntimeException e) {
            log.warn("Geocoding falhou para '{}': {}", clean, e.getMessage());
            return Optional.empty();
        }
    }

    private int persist(UUID runId, String packageId, String resourceId, Map<String, Item> byRecord,
                        Map<String, GeocodeResult> coords, Set<String> inOtherResources, RunCounters c,
                        long[] resolvedOut) {
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
        List<UUID> nowParseable = new ArrayList<>();
        int written = 0;
        int duplicates = 0;
        for (Item it : byRecord.values()) {
            String id = it.record().recordId();
            OccurrenceClassification cls = it.cls();
            if (cls.status() == Status.TS_UNPARSEABLE) {
                entries.add(new QuarantineRepository.Entry(rawIds.get(id), rawAt.get(id), "TS", "TS_UNPARSEABLE",
                        Map.of("recordId", id, "detail", String.valueOf(cls.detail()))));
                continue;
            }
            if (cls.status() != Status.ACCEPTED && cls.status() != Status.EXCLUDED_BY_FILTER) continue;
            nowParseable.add(rawIds.get(id));
            if (cls.status() != Status.ACCEPTED) continue;
            if (inOtherResources.contains(identity(it))) {
                // mesma ocorrência já registrada por outro recurso do CKAN (recursos duplicados): uma só vez
                duplicates++;
                continue;
            }
            GeocodeResult g = coords.get(id);
            written += occurrences.upsert(new OccurrenceRow(packageId, resourceId, id, it.hash(), runId,
                    cls.address(), cls.occurredFrom(), cls.occurredTo(), cls.precision(), null,
                    "TEXT_MATCH:" + cls.matchedText(), g == null ? null : g.lat(), g == null ? null : g.lon(),
                    g == null ? "NOT_GEOCODED" : GEOCODE_METHOD,
                    g == null ? GeocodeLevel.NONE.name() : g.level() == null ? null : g.level().name(),
                    clip(cls.neighborhood(), MAX_NEIGHBORHOOD)));
        }
        quarantine.insertIfAbsent(runId, props.getRuleVersion(), now, entries);
        // quarentena TS aberta de um bruto cuja data agora é interpretável (mapeamento de colunas): fecha, não apaga
        resolvedOut[0] += quarantine.resolveByRawIds(now, "REVALIDATED:" + runId, "TS", nowParseable);
        c.addQuarantined(entries.size());
        c.addValid(written);
        if (duplicates > 0) log.info("CKAN {}: {} ocorrência(s) já presentes em outro recurso, não regravadas", resourceId, duplicates);
        return written;
    }

    /** Tolerância (graus, ~1 m) para considerar que o geocodificador devolveu a mesma coordenada gravada. */
    static final double SAME_COORDINATE_EPS = 1e-5;

    @Override
    public GeocodeLevelBackfill backfillGeocodeLevels() {
        int examined = 0, none = 0, street = 0, area = 0, city = 0, unverifiable = 0, filled = 0;
        for (OccurrenceRepository.PendingLevel p : occurrences.pendingGeocodeLevel()) {
            examined++;
            // bairro: lido do bruto (campo Bairro, igual nos dois mapeamentos), nunca deduzido
            String bairro = raws.latestPayload(SOURCE, p.resourceId() + "#" + p.recordId())
                    .map(n -> clip(n.path("Bairro").asText(""), MAX_NEIGHBORHOOD)).orElse(null);
            String level = null;
            if (p.lat() == null || p.lon() == null) {
                level = GeocodeLevel.NONE.name();
            } else {
                String clean = CkanAddress.sanitize(p.address());
                Optional<GeocodeResult> g = clean.isBlank() && bairro == null ? Optional.empty()
                        : geocodeQuietly(clean, bairro);
                if (g.isPresent() && g.get().level() != null
                        && Math.abs(g.get().lat() - p.lat()) < SAME_COORDINATE_EPS
                        && Math.abs(g.get().lon() - p.lon()) < SAME_COORDINATE_EPS) {
                    level = g.get().level().name();
                }
            }
            if (level == null && p.lat() != null) unverifiable++;
            if (level != null) switch (GeocodeLevel.valueOf(level)) {
                case NONE -> none++;
                case STREET -> street++;
                case NEIGHBORHOOD -> area++;
                case CITY -> city++;
            }
            if (bairro != null) filled++;
            occurrences.setGeocodeInfo(p.id(), level, bairro);
        }
        return new GeocodeLevelBackfill(examined, none, street, area, city, unverifiable, filled);
    }

    private Optional<GeocodeResult> geocodeQuietly(String clean, String bairro) {
        try {
            return geocoding.geocodeWithLevel(clean, bairro, "Recife");
        } catch (RuntimeException e) {
            log.warn("Geocoding (backfill) falhou para '{}': {}", clean, e.getMessage());
            return Optional.empty();
        }
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
