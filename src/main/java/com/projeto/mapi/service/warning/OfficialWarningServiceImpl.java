package com.projeto.mapi.service.warning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.OfficialWarningDTO;
import com.projeto.mapi.dto.OfficialWarningsResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.ingestion.IngestionRunRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawInsert;
import com.projeto.mapi.repository.ingestion.RawObservationRepository.RawVersion;
import com.projeto.mapi.repository.warning.OfficialWarningRepository;
import com.projeto.mapi.service.ingestion.OriginKey;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.RunCounters;
import com.projeto.mapi.service.warning.InmetWarningParser.ParseResult;
import com.projeto.mapi.service.warning.InmetWarningParser.ParsedWarning;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Coleta os avisos ativos do INMET fora da transação, registra bruto em {@code raw_observation} (ADR 0006)
 * e persiste os que tocam os municípios dos pontos críticos em {@code official_warning} (ADR 0024).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OfficialWarningServiceImpl implements OfficialWarningService {

    static final String PARSER_VERSION = "inmet-avisos/1";
    static final String RULE_VERSION = "adr-0024/1";

    private final InmetWarningClient client;
    private final OfficialWarningRepository warnings;
    private final FloodPointRepository floodPoints;
    private final IngestionRunRepository runs;
    private final RawObservationRepository raws;
    private final AppProperties appProps;
    private final IngestionProperties ingestionProps;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final TransactionTemplate tx;

    @Override
    public int syncInmetActive() {
        Instant startedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID runId = UUID.randomUUID();
        Set<String> targetGeocodes = resolveTargetGeocodes();
        ObjectNode params = mapper.createObjectNode();
        params.putPOJO("targetGeocodes", targetGeocodes);
        runs.create(runId, InmetWarningParser.SOURCE, com.projeto.mapi.service.ingestion.IngestionKind.LIVE.name(),
                com.projeto.mapi.service.ingestion.IngestionMode.REFETCH.name(), null, null,
                ingestionProps.getCodeVersion(), PARSER_VERSION, RULE_VERSION, startedAt, null, params);

        RunCounters c = new RunCounters();
        String status = "OK";
        String error = null;
        int persisted = 0;
        try {
            String json = client.fetchActiveJson();
            ParseResult parsed = InmetWarningParser.parse(json, mapper);
            c.addFetched(parsed.totalRawItems());
            List<ParsedWarning> matching = parsed.warnings().stream()
                    .filter(w -> w.touchesAny(targetGeocodes))
                    .toList();
            c.addValid(matching.size());
            Instant seenAt = clock.instant().truncatedTo(ChronoUnit.MICROS);

            persisted = Boolean.TRUE.equals(tx.execute(s -> persistBatch(runId, seenAt, matching, c)))
                    ? matching.size() : 0;
            return persisted;
        } catch (RuntimeException e) {
            status = "FAILED";
            error = e.getMessage();
            log.warn("Falha ao sincronizar avisos do INMET: {}", e.toString());
            throw e;
        } finally {
            Instant finishedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
            runs.finish(runId, status, finishedAt, c, error, params);
        }
    }

    private boolean persistBatch(UUID runId, Instant seenAt, List<ParsedWarning> matching, RunCounters c) {
        if (matching.isEmpty()) return true;
        List<RawRecord> recs = matching.stream()
                .map(w -> new RawRecord(
                        InmetWarningParser.SOURCE,
                        w.warningId() + ":" + w.sequenceId(),
                        seenAt,
                        w.inicioText(),
                        w.validFrom().toString(),
                        InmetWarningParser.SOURCE_TZ,
                        w.rawPayload()))
                .toList();
        List<OriginKey> keys = recs.stream().map(RawRecord::key).toList();
        Map<OriginKey, RawVersion> latest = raws.latestVersions(InmetWarningParser.SOURCE, keys);
        List<RawInsert> inserts = new ArrayList<>();
        for (int i = 0; i < matching.size(); i++) {
            RawRecord rec = recs.get(i);
            String hash = matching.get(i).rawHash();
            RawVersion prev = latest.get(rec.key());
            if (prev != null && prev.contentHash().equals(hash)) {
                c.addRawUnchanged(1);
            } else {
                inserts.add(new RawInsert(UUID.randomUUID(), rec, hash));
                c.addRawInserted(1);
            }
        }
        raws.insertAll(runId, seenAt, inserts);
        int touched = warnings.upsertAll(InmetWarningParser.SOURCE, runId, seenAt, matching);
        c.addObsInserted(touched);
        return true;
    }

    @Override
    public OfficialWarningsResponseDTO activeWarnings(String municipio) {
        Instant now = clock.instant();
        String ibgeFilter = null;
        String patternFilter = null;
        if (municipio != null && !municipio.isBlank()) {
            Optional<String> ibge = InmetWarningParser.resolveIbge(municipio);
            if (ibge.isPresent()) {
                ibgeFilter = ibge.get();
            } else {
                patternFilter = municipio.trim();
            }
        }
        List<OfficialWarningDTO> list = warnings.findActive(now, ibgeFilter, patternFilter);
        return new OfficialWarningsResponseDTO(
                OfficialWarningsResponseDTO.SOURCE_LABEL,
                OfficialWarningsResponseDTO.ATTRIBUTION,
                OfficialWarningsResponseDTO.EMISSION_NOTE,
                now,
                list
        );
    }

    Set<String> resolveTargetGeocodes() {
        Set<String> out = new LinkedHashSet<>(appProps.getInmet().getTargetGeocodes());
        for (FloodPoint fp : floodPoints.findByActiveTrue()) {
            InmetWarningParser.resolveIbge(fp.getMunicipality()).ifPresent(out::add);
        }
        return Set.copyOf(out);
    }
}
