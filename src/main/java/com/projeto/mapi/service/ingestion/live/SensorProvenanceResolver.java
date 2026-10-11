package com.projeto.mapi.service.ingestion.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.ingestion.RawObservationRepository;
import com.projeto.mapi.service.ingestion.RawRecord;
import com.projeto.mapi.service.ingestion.adapter.AnaSoapAdapter;
import com.projeto.mapi.service.ingestion.adapter.ApacMeteo24hAdapter;
import com.projeto.mapi.service.ingestion.adapter.CemadenAdapter;
import com.projeto.mapi.service.ingestion.adapter.SensorEnvelopeAdapter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Proveniência de uma leitura legada (sensor_data) de ANA/APAC/Cemaden: o {@code run_id} do ingestion_run que
 * gravou o MESMO envelope no staging bruto (o legado guarda o envelope em {@code raw_data}). Como o fuso dessas
 * fontes é UNVERIFIED, o run é o da gravação no bruto (e da quarentena), não de uma observação consolidada;
 * por isso {@code revision} fica nulo. Sem migração: a ligação é recalculada pela chave de origem.
 */
@Component
@Slf4j
public class SensorProvenanceResolver {

    public record Provenance(UUID runId, String source) {}

    private final RawObservationRepository raws;
    private final ObjectMapper mapper;
    private final AnaSoapAdapter ana;
    private final ApacMeteo24hAdapter meteo;
    private final CemadenAdapter cemaden;

    public SensorProvenanceResolver(RawObservationRepository raws, ObjectMapper mapper, AnaSoapAdapter ana,
                                    ApacMeteo24hAdapter meteo, CemadenAdapter cemaden) {
        this.raws = raws;
        this.mapper = mapper;
        this.ana = ana;
        this.meteo = meteo;
        this.cemaden = cemaden;
    }

    public Optional<Provenance> resolve(SensorData data) {
        if (data == null || data.getRawData() == null) return Optional.empty();
        try {
            JsonNode envelope = mapper.readTree(data.getRawData());
            SensorEnvelopeAdapter adapter = adapterFor(data, envelope);
            if (adapter == null) return Optional.empty();
            RawRecord raw = adapter.toRaw(envelope, Instant.EPOCH);
            return raws.latestRunId(adapter.source(), raw.stationId(), raw.sourceTsNorm())
                    .map(id -> new Provenance(id, adapter.source()));
        } catch (Exception e) {
            log.debug("Sem proveniência para o sensor {}: {}", data.getSensorId(), e.getMessage());
            return Optional.empty();
        }
    }

    private SensorEnvelopeAdapter adapterFor(SensorData data, JsonNode envelope) {
        String fonte = envelope.path("fonte").asText("");
        if (fonte.contains("Cemaden")) return cemaden;
        if (fonte.contains("Meteorologia24h")) return meteo;
        String id = data.getSensorId() == null ? "" : data.getSensorId();
        if (id.startsWith("ANA")) return ana;
        return null;
    }
}
