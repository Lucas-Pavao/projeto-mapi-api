package com.projeto.mapi.service.tide;

import com.projeto.mapi.repository.ingestion.ObservationReadRepository;
import com.projeto.mapi.repository.ingestion.ObservationReadRepository.TideExtremeRow;
import com.projeto.mapi.service.ingestion.IngestionMode;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.adapter.tide.TabuaMareApiAdapter;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class TideObservationSyncTaskTest {

    private final IngestionPipelineService pipeline = mock(IngestionPipelineService.class);
    /** Extremos que o repositório devolve (o Mockito do JDK 25 não simula a classe concreta). */
    private List<TideExtremeRow> extremes = List.of();
    private final ObservationReadRepository observations = new ObservationReadRepository(null) {
        @Override
        public List<TideExtremeRow> tideExtremes(Instant fromUtc, Instant toUtc) {
            return extremes;
        }
    };

    private TideObservationSyncTask task(String now) {
        return new TideObservationSyncTask(pipeline, observations, Clock.fixed(Instant.parse(now), ZoneOffset.UTC));
    }

    private static TideExtremeRow row(Instant t) {
        return new TideExtremeRow(t, 1.0, "PREAMAR", TabuaMareApiAdapter.SOURCE);
    }

    @Test
    void semMareCarregaDeTresDiasAtrasADezAFrenteEmDiasLocais() {
        task("2026-10-22T01:00:00Z").syncIfMissing(); // 21/10 22:00 local

        ArgumentCaptor<IngestionRequest> req = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(pipeline).execute(req.capture());
        assertThat(req.getValue().source()).isEqualTo(TabuaMareApiAdapter.SOURCE);
        assertThat(req.getValue().mode()).isEqualTo(IngestionMode.REFETCH);
        assertThat(req.getValue().from()).isEqualTo(Instant.parse("2026-10-18T03:00:00Z"));
        assertThat(req.getValue().to()).isEqualTo(Instant.parse("2026-11-01T03:00:00Z"));
    }

    @Test
    void coberturaSuficienteNaoChamaAFonte() {
        Instant now = Instant.parse("2026-10-22T01:00:00Z");
        extremes = List.of(row(now.minus(Duration.ofHours(5))), row(now.plus(Duration.ofDays(3)).plus(Duration.ofHours(2))));
        task(now.toString()).syncIfMissing();
        verify(pipeline, never()).execute(any());
    }

    @Test
    void naoPassaDe31DeDezembro() {
        task("2026-12-28T15:00:00Z").syncIfMissing();
        ArgumentCaptor<IngestionRequest> req = ArgumentCaptor.forClass(IngestionRequest.class);
        verify(pipeline).execute(req.capture());
        assertThat(req.getValue().to()).isEqualTo(Instant.parse("2027-01-01T03:00:00Z"));
    }
}
