package com.projeto.mapi.service.ingestion.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.repository.ingestion.ObservationRepository;
import com.projeto.mapi.repository.ingestion.ObservationRepository.ObsRow;
import com.projeto.mapi.repository.ingestion.QuarantineRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawInsert;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawVersion;
import com.projeto.mapi.repository.ingestion.SourceRegistryRepository;
import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.ingestion.adapter.SourceAdapter;
import com.projeto.mapi.service.ingestion.validation.RecordValidation;
import com.projeto.mapi.service.ingestion.validation.Rejection;
import com.projeto.mapi.service.ingestion.validation.SourceTzRule;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import com.projeto.mapi.service.ingestion.validation.ValidObservation;
import com.projeto.mapi.service.ingestion.validation.ValidationService;
import com.projeto.mapi.service.ingestion.validation.VariableRule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pipeline do ADR 0006: staging bruto (dedupe contra a versão mais recente) -> validação com quarentena ->
 * upsert versionado em observation. Uma transação por lote, serializada por fonte com advisory lock.
 */
@Service
@Slf4j
public class IngestionPipelineServiceImpl implements IngestionPipelineService {

    private record WorkItem(RawRecord record, UUID rawId, Instant rawIngestedAt, String hash) {}

    private record Candidate(WorkItem item, ValidObservation obs) {}

    private static class RunState {
        final RunCounters counters = new RunCounters();
        final ArrayNode requests;
        final Set<OriginKey> fetchedKeys = new HashSet<>();
        int batchesOk;
        int batchesFailed;
        final StringBuilder errors = new StringBuilder();

        RunState(ArrayNode requests) {
            this.requests = requests;
        }
    }

    private final Map<String, SourceAdapter> adapters;
    private final IngestionRunRepository runs;
    private final RawObservationRepository raws;
    private final ObservationRepository observations;
    private final QuarantineRepository quarantine;
    private final SourceRegistryRepository registry;
    private final ValidationService validation;
    private final IngestionProperties props;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final TransactionTemplate tx;
    private final Executor taskExecutor;

    public IngestionPipelineServiceImpl(List<SourceAdapter> adapterList, IngestionRunRepository runs,
                                        RawObservationRepository raws, ObservationRepository observations,
                                        QuarantineRepository quarantine, SourceRegistryRepository registry,
                                        ValidationService validation, IngestionProperties props, Clock clock,
                                        ObjectMapper mapper, PlatformTransactionManager txManager,
                                        Executor taskExecutor) {
        this.adapters = adapterList.stream().collect(Collectors.toMap(SourceAdapter::source, Function.identity()));
        this.runs = runs;
        this.raws = raws;
        this.observations = observations;
        this.quarantine = quarantine;
        this.registry = registry;
        this.validation = validation;
        this.props = props;
        this.clock = clock;
        this.mapper = mapper;
        this.tx = new TransactionTemplate(txManager);
        this.taskExecutor = taskExecutor;
    }

    @Override
    public IngestionRunView execute(IngestionRequest request) {
        UUID id = open(request);
        return runOpened(id, request);
    }

    @Override
    public UUID submit(IngestionRequest request) {
        UUID id = open(request);
        taskExecutor.execute(() -> runOpened(id, request));
        return id;
    }

    @Override
    public Optional<IngestionRunView> findRun(UUID id) {
        return runs.findById(id);
    }

    // ------------------------------------------------------------------------------------------

    private UUID open(IngestionRequest req) {
        SourceAdapter adapter = adapters.get(req.source());
        if (adapter == null) {
            throw new InvalidRequestException("Fonte sem adaptador no pipeline: " + req.source()
                    + " (disponíveis: " + adapters.keySet() + ")");
        }
        if (req.from() == null || req.to() == null || !req.from().isBefore(req.to())) {
            throw new InvalidRequestException("Intervalo inválido: exige from < to (intervalo [from, to))");
        }
        if (req.mode() == null) throw new InvalidRequestException("mode é obrigatório (REFETCH ou REVALIDATE)");
        UUID id = UUID.randomUUID();
        IngestionKind kind = req.kind() == null ? IngestionKind.REPROCESS : req.kind();
        ObjectNode params = mapper.createObjectNode();
        params.put("source", req.source());
        params.put("from", req.from().toString());
        params.put("to", req.to().toString());
        params.put("mode", req.mode().name());
        UUID parent = runs.lastSuccessfulRun(req.source(), req.from(), req.to()).orElse(null);
        runs.create(id, req.source(), kind.name(), req.mode().name(), req.from(), req.to(), props.getCodeVersion(),
                adapter.parserVersion(), props.getRuleVersion(), clock.instant().truncatedTo(ChronoUnit.MICROS),
                parent, params);
        return id;
    }

    private IngestionRunView runOpened(UUID runId, IngestionRequest req) {
        SourceAdapter adapter = adapters.get(req.source());
        RunState st = new RunState(mapper.createArrayNode());
        boolean fetchFailed = false;
        try {
            if (req.mode() == IngestionMode.REFETCH) {
                adapter.fetch(req.from(), req.to(), batch -> {
                    st.requests.add(batch.requestInfo());
                    try {
                        tx.executeWithoutResult(s -> processFetched(runId, adapter, batch, st));
                        st.batchesOk++;
                    } catch (RuntimeException e) {
                        st.batchesFailed++;
                        st.errors.append("lote falhou: ").append(e).append("; ");
                        log.error("Lote do run {} falhou", runId, e);
                    }
                });
                countAbsent(adapter, req, st);
            } else {
                revalidate(runId, adapter, req, st);
            }
        } catch (SourceUnavailableException e) {
            fetchFailed = true;
            st.errors.append("fonte indisponível: ").append(e.getMessage());
        } catch (RuntimeException e) {
            fetchFailed = true;
            st.errors.append("erro: ").append(e);
            log.error("Run {} falhou", runId, e);
        }
        String status;
        if (st.batchesFailed == 0 && !fetchFailed) status = "OK";
        else if (st.batchesOk > 0) status = "PARTIAL";
        else status = "FAILED";
        ObjectNode params = mapper.createObjectNode();
        params.put("source", req.source());
        params.put("from", req.from().toString());
        params.put("to", req.to().toString());
        params.put("mode", req.mode().name());
        params.set("requests", st.requests);
        runs.finish(runId, status, clock.instant().truncatedTo(ChronoUnit.MICROS), st.counters,
                st.errors.length() == 0 ? null : st.errors.toString(), params);
        return runs.findById(runId).orElseThrow();
    }

    // ------------------------------------------------------------------------------------------

    private void processFetched(UUID runId, SourceAdapter adapter, FetchedBatch batch, RunState st) {
        String source = adapter.source();
        runs.lockSource(source);
        registry.upsertStations(batch.stations());
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        // dedupe do lote por registro de origem: vence o mais recente (collectedAt; empate = o último da lista)
        Map<OriginKey, RawRecord> byKey = new LinkedHashMap<>();
        for (RawRecord r : batch.records()) {
            RawRecord prev = byKey.get(r.key());
            if (prev == null || !r.collectedAt().isBefore(prev.collectedAt())) byKey.put(r.key(), r);
        }
        st.counters.addFetched(byKey.size());
        st.counters.addBatchDeduped(batch.records().size() - byKey.size());
        st.fetchedKeys.addAll(byKey.keySet());

        Map<OriginKey, RawVersion> latest = raws.latestVersions(source, byKey.keySet());
        List<RawInsert> inserts = new ArrayList<>();
        List<WorkItem> items = new ArrayList<>();
        for (RawRecord r : byKey.values()) {
            String hash = Hashing.contentHash(r.payload());
            RawVersion prev = latest.get(r.key());
            if (prev != null && prev.contentHash().equals(hash)) {
                st.counters.addRawUnchanged(1);
                items.add(new WorkItem(r, prev.id(), prev.ingestedAt(), hash));
            } else {
                UUID id = UUID.randomUUID();
                inserts.add(new RawInsert(id, r, hash));
                items.add(new WorkItem(r, id, now, hash));
            }
        }
        raws.insertAll(runId, now, inserts);
        st.counters.addRawInserted(inserts.size());
        process(runId, adapter, items, now, st.counters);
    }

    private void revalidate(UUID runId, SourceAdapter adapter, IngestionRequest req, RunState st) {
        List<RawVersion> selected = selectBySourceInstant(adapter.source(), req.from(), req.to());
        int size = props.getBatchSize();
        for (int i = 0; i < selected.size(); i += size) {
            List<RawVersion> chunk = selected.subList(i, Math.min(selected.size(), i + size));
            try {
                tx.executeWithoutResult(s -> {
                    runs.lockSource(adapter.source());
                    Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
                    List<WorkItem> items = chunk.stream()
                            .map(v -> new WorkItem(v.record(), v.id(), v.ingestedAt(), v.contentHash())).toList();
                    st.counters.addFetched(items.size());
                    process(runId, adapter, items, now, st.counters);
                });
                st.batchesOk++;
            } catch (RuntimeException e) {
                st.batchesFailed++;
                st.errors.append("lote falhou: ").append(e).append("; ");
            }
        }
        if (selected.isEmpty()) st.batchesOk++;
    }

    /** Registros de origem cujo instante cai em [from,to) pelo fuso declarado no bruto OU pelo fuso atual. */
    private List<RawVersion> selectBySourceInstant(String source, Instant from, Instant to) {
        String lo = from.minus(props.getRevalidateMarginDays(), ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate().toString();
        String hi = to.plus(props.getRevalidateMarginDays(), ChronoUnit.DAYS).atZone(ZoneOffset.UTC).toLocalDate().toString();
        SourceTzRule current = registry.findTz(source).orElse(new SourceTzRule("UNVERIFIED", "UNVERIFIED"));
        return raws.latestInTextWindow(source, lo, hi).stream().filter(v -> {
            Instant a = instantOrNull(v.record().sourceTsNorm(), v.record().sourceTz());
            Instant b = instantOrNull(v.record().sourceTsNorm(), current.tz());
            if (a == null && b == null) a = instantOrNull(v.record().sourceTsNorm(), "UTC");
            return inWindow(a, from, to) || inWindow(b, from, to);
        }).toList();
    }

    private static boolean inWindow(Instant i, Instant from, Instant to) {
        return i != null && !i.isBefore(from) && i.isBefore(to);
    }

    private static Instant instantOrNull(String norm, String tz) {
        try {
            return TimestampNormalizer.toInstant(norm, tz);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private void countAbsent(SourceAdapter adapter, IngestionRequest req, RunState st) {
        if (st.batchesFailed > 0) return;
        Set<OriginKey> existing = selectBySourceInstant(adapter.source(), req.from(), req.to()).stream()
                .map(v -> v.record().key()).collect(Collectors.toSet());
        existing.removeAll(st.fetchedKeys);
        st.counters.setAbsentInRefetch(existing.size());
    }

    // ------------------------------------------------------------------------------------------

    /** Valida, resolve conflitos, faz o upsert versionado, retira linhas deslocadas e atualiza a quarentena. */
    private void process(UUID runId, SourceAdapter adapter, List<WorkItem> items, Instant now, RunCounters c) {
        String source = adapter.source();
        String ruleVersion = props.getRuleVersion();
        SourceTzRule tz = registry.findTz(source).orElse(new SourceTzRule("UNVERIFIED", "UNVERIFIED"));
        Map<String, VariableRule> catalog = registry.catalog();

        List<Candidate> candidates = new ArrayList<>();
        List<QuarantineRepository.Entry> entries = new ArrayList<>();
        Map<UUID, List<String>> reasonsByRaw = new LinkedHashMap<>();
        for (WorkItem item : items) {
            List<DraftValue> drafts = adapter.extract(item.record());
            RecordValidation rv = validation.validate(item.record(), tz, drafts, catalog);
            rv.valid().forEach(v -> candidates.add(new Candidate(item, v)));
            for (Rejection rj : rv.rejections()) {
                entries.add(new QuarantineRepository.Entry(item.rawId(), item.rawIngestedAt(), rj.ruleId(),
                        rj.reason(), rj.detail()));
                reasonsByRaw.computeIfAbsent(item.rawId(), k -> new ArrayList<>()).add(rj.ruleId() + ":" + rj.reason());
            }
        }

        // Duplicata: registros de origem DISTINTOS na mesma chave natural. Valores iguais: vence o mais recente;
        // valores diferentes: todos para a quarentena CONFLICT_IN_BATCH.
        Map<String, List<Candidate>> byKey = new LinkedHashMap<>();
        for (Candidate cd : candidates) {
            byKey.computeIfAbsent(naturalKey(cd), k -> new ArrayList<>()).add(cd);
        }
        List<Candidate> kept = new ArrayList<>();
        for (List<Candidate> group : byKey.values()) {
            if (group.size() == 1) {
                kept.add(group.get(0));
            } else if (group.stream().map(g -> g.obs().valueHash()).distinct().count() == 1) {
                kept.add(group.stream().max(Comparator.comparing((Candidate g) -> g.item().record().collectedAt())).get());
            } else {
                for (Candidate g : group) {
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("variable", g.obs().variable());
                    d.put("tsUtc", g.obs().tsUtc().toString());
                    d.put("value", g.obs().value());
                    entries.add(new QuarantineRepository.Entry(g.item().rawId(), g.item().rawIngestedAt(),
                            "DUP", "CONFLICT_IN_BATCH", d));
                    reasonsByRaw.computeIfAbsent(g.item().rawId(), k -> new ArrayList<>()).add("DUP:CONFLICT_IN_BATCH");
                }
            }
        }

        List<ObsRow> rows = flagGaps(kept, c);
        int[] ins = observations.upsert(runId, source, ruleVersion, rows);
        c.addObsInserted(ins[0]);
        c.addObsUpdated(ins[1]);
        c.addObsUnchanged(rows.size() - ins[0] - ins[1]);
        c.addValid(rows.size());

        List<String> originStations = items.stream().map(i -> i.record().stationId()).toList();
        List<String> originNorms = items.stream().map(i -> i.record().sourceTsNorm()).toList();
        c.addObsRetracted(observations.retractNotKept(runId, source, originStations, originNorms, rows));

        quarantine.resolveStale(now, "REVALIDATED_BY_" + runId, source, items.stream()
                .map(i -> new QuarantineRepository.Reviewed(i.rawId(), source, i.record().stationId(),
                        i.record().sourceTsNorm(), reasonsByRaw.getOrDefault(i.rawId(), List.of()))).toList());
        quarantine.insertIfAbsent(runId, ruleVersion, now, entries);
        c.addQuarantined(entries.size());
    }

    private static String naturalKey(Candidate cd) {
        return cd.item().record().stationId() + "|" + cd.obs().variable() + "|" + cd.obs().resolution() + "|"
                + cd.obs().tsUtc();
    }

    /** Marca GAP_BEFORE na série (estação, variável, resolução) quando o salto passa do passo; nunca preenche. */
    private List<ObsRow> flagGaps(List<Candidate> kept, RunCounters c) {
        Map<String, List<Candidate>> series = new LinkedHashMap<>();
        for (Candidate cd : kept) {
            series.computeIfAbsent(cd.item().record().stationId() + "|" + cd.obs().variable() + "|"
                    + cd.obs().resolution(), k -> new ArrayList<>()).add(cd);
        }
        List<ObsRow> rows = new ArrayList<>();
        for (List<Candidate> s : series.values()) {
            s.sort(Comparator.comparing(cd -> cd.obs().tsUtc()));
            String res = s.get(0).obs().resolution();
            Duration step = res.startsWith("PT") ? Duration.parse(res) : null;
            Instant prev = null;
            for (Candidate cd : s) {
                ValidObservation o = cd.obs();
                String flag = "OK";
                if (step != null && prev != null && Duration.between(prev, o.tsUtc()).compareTo(step) > 0) {
                    flag = "GAP_BEFORE";
                    c.addGaps(1);
                }
                prev = o.tsUtc();
                rows.add(new ObsRow(cd.item().record().stationId(), o.variable(), o.resolution(), o.tsUtc(), o.value(),
                        o.unit(), o.qualifier(), o.intervalStartUtc(), flag, cd.item().hash(), o.valueHash(),
                        cd.item().record().sourceTsNorm()));
            }
        }
        return rows;
    }
}
