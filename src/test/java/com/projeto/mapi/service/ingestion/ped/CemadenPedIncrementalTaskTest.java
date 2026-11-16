package com.projeto.mapi.service.ingestion.ped;

import com.projeto.mapi.config.IngestionProperties;
import com.projeto.mapi.dto.PointRainRebuildDTO;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.service.analise.PointRainService;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Sem Mockito (o inline não roda no JDK do ambiente): stubs das interfaces e subclasse do repositório. */
class CemadenPedIncrementalTaskTest {

    private static final Instant NOW = Instant.parse("2026-11-16T10:42:00Z");

    private final List<IngestionRequest> requests = new ArrayList<>();
    private final List<Instant[]> rebuilds = new ArrayList<>();
    private String status = "OK";

    private CemadenPedIncrementalTask task(Instant lastPed) {
        IngestionPipelineService pipeline = new IngestionPipelineService() {
            public IngestionRunView execute(IngestionRequest r) {
                requests.add(r);
                return new IngestionRunView(UUID.randomUUID(), r.source(), "BACKFILL", "REFETCH", status, r.from(), r.to(),
                        NOW, NOW, null, null, null, null, null, 0, 0, 0, 0, 0, 0, 0, 10, 0, 0, 0, 0, null);
            }
            public UUID submit(IngestionRequest r) { throw new UnsupportedOperationException(); }
            public IngestionRunView executePushed(String s, FetchedBatch b) { throw new UnsupportedOperationException(); }
            public Optional<IngestionRunView> findRun(UUID id) { return Optional.empty(); }
        };
        PointRainService rain = (from, to) -> {
            rebuilds.add(new Instant[]{from, to});
            return new PointRainRebuildDTO(from, to, 0, 0, 0, Map.of(), Map.of(), 0, Map.of(), 0, Map.of(), List.of());
        };
        ObservationReadRepository obs = new ObservationReadRepository(null) {
            @Override
            public Optional<Instant> latestTs(String source, String variable, Instant since) {
                return lastPed == null || lastPed.isBefore(since) ? Optional.empty() : Optional.of(lastPed);
            }
        };
        IngestionProperties props = new IngestionProperties();
        CemadenPedIncrementalTask t = new CemadenPedIncrementalTask(props, pipeline, rain, obs);
        t.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
        return t;
    }

    @Test
    void retomaDaUltimaLeituraMenosSobreposicao_eRecalculaDesdeODia1DoMes() {
        assertThat(task(Instant.parse("2026-10-07T23:50:00Z")).runOnce()).isPresent();
        IngestionRequest r = requests.get(0);
        assertThat(r.source()).isEqualTo("CEMADEN_PED");
        assertThat(r.from()).isEqualTo(Instant.parse("2026-10-07T17:00:00Z"));   // 23:50 − 6 h, hora cheia
        assertThat(r.to()).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));     // hora cheia atual
        // checagem é por mês inteiro: recalcula outubro todo, não só a cauda
        assertThat(rebuilds.get(0)[0]).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
        assertThat(rebuilds.get(0)[1]).isEqualTo(Instant.parse("2026-11-16T10:00:00Z"));
    }

    @Test
    void semPedRecente_naoVoltaMaisQueOLimite() {
        task(null).runOnce();
        assertThat(requests.get(0).from()).isEqualTo(Instant.parse("2026-09-17T10:00:00Z")); // 60 dias
    }

    @Test
    void runFalhou_naoRecalcula() {
        status = "FAILED";
        assertThat(task(Instant.parse("2026-11-16T09:50:00Z")).runOnce()).isEmpty();
        assertThat(rebuilds).isEmpty();
    }

    @Test
    void runParcial_recalculaComOQueChegou() {
        status = "PARTIAL";
        assertThat(task(Instant.parse("2026-11-16T09:50:00Z")).runOnce()).isPresent();
        assertThat(rebuilds.get(0)[0]).isEqualTo(Instant.parse("2026-11-01T00:00:00Z"));
    }
}
