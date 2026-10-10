package com.projeto.mapi.service.flood;

import com.projeto.mapi.service.MapiService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADR 0007 §7: o passo de predição vai para o taskExecutor e não empilha rodadas. */
class PredictionCycleRunnerTest {

    private final MapiService mapiService = mock(MapiService.class);

    @Test
    void agendaNoExecutorSemRodarNaThreadDoAgendador() {
        List<Runnable> fila = new ArrayList<>();
        PredictionCycleRunner runner = new PredictionCycleRunner(mapiService, fila::add);

        assertThat(runner.triggerAfterCycle("ana")).isTrue();

        assertThat(fila).hasSize(1);
        verify(mapiService, times(0)).refreshPredictionsForActivePoints();
        fila.get(0).run();
        verify(mapiService).refreshPredictionsForActivePoints();
    }

    @Test
    void descartaDisparoEnquantoARodadaAnteriorAindaEstaEmAndamento() {
        List<Runnable> fila = new ArrayList<>();
        PredictionCycleRunner runner = new PredictionCycleRunner(mapiService, fila::add);

        assertThat(runner.triggerAfterCycle("ana")).isTrue();
        assertThat(runner.triggerAfterCycle("apac")).isFalse();
        fila.get(0).run();
        assertThat(runner.triggerAfterCycle("apac")).isTrue();
    }

    @Test
    void falhaNaRodadaLiberaOProximoDisparo() {
        List<Runnable> fila = new ArrayList<>();
        when(mapiService.refreshPredictionsForActivePoints()).thenThrow(new IllegalStateException("boom"));
        PredictionCycleRunner runner = new PredictionCycleRunner(mapiService, fila::add);

        runner.triggerAfterCycle("ana");
        fila.get(0).run();

        assertThat(runner.triggerAfterCycle("ana")).isTrue();
    }

    @Test
    void poolCheioNaoPropagaEDevolveFalse() {
        Executor cheio = r -> {
            throw new RejectedExecutionException("cheio");
        };
        PredictionCycleRunner runner = new PredictionCycleRunner(mapiService, cheio);

        assertThat(runner.triggerAfterCycle("ana")).isFalse();
    }
}
