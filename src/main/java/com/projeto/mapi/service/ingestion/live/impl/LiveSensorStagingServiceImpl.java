package com.projeto.mapi.service.ingestion.live.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.adapter.SensorEnvelopeAdapter;
import com.projeto.mapi.service.ingestion.live.LiveSensorStagingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class LiveSensorStagingServiceImpl implements LiveSensorStagingService {

    private final Map<String, SensorEnvelopeAdapter> adapters;
    private final IngestionPipelineService pipeline;
    private final ObjectMapper mapper;
    private final Clock clock;

    public LiveSensorStagingServiceImpl(List<SensorEnvelopeAdapter> adapters, IngestionPipelineService pipeline,
                                        ObjectMapper mapper, Clock clock) {
        this.adapters = adapters.stream().collect(Collectors.toMap(SensorEnvelopeAdapter::source, Function.identity()));
        this.pipeline = pipeline;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public int stage(String source, List<JsonNode> envelopes) {
        if (envelopes == null || envelopes.isEmpty()) return 0;
        try {
            SensorEnvelopeAdapter adapter = adapters.get(source);
            if (adapter == null) {
                log.warn("Staging ao vivo ignorado: fonte sem adaptador {}", source);
                return 0;
            }
            ObjectNode req = mapper.createObjectNode();
            req.put("kind", "LIVE_PUSHED");
            req.put("records", envelopes.size());
            // As estações vão junto: sem elas a tabela station fica vazia e a chuva horária sai sem coordenadas
            // (mapa público, alertas por raio).
            FetchedBatch batch = adapter.snapshotBatch(envelopes, clock.instant(), req);
            IngestionRunView run = pipeline.executePushed(source, batch);
            log.info("Pipeline ao vivo {} run {}: {} (fetched={}, valid={}, quarantined={})", source, run.id(),
                    run.status(), run.fetched(), run.valid(), run.quarantined());
            return batch.records().size();
        } catch (RuntimeException e) {
            log.error("Staging ao vivo de {} falhou (fluxo legado não é afetado): {}", source, e.toString());
            return 0;
        }
    }
}
