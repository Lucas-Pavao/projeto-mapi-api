package com.projeto.mapi.service.flood;

import com.projeto.mapi.service.MapiService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Passo de predição ao fim de cada ciclo do {@code SensorCollectionTask} (ADR 0007 §7). Roda no pool
 * {@code taskExecutor}, então nunca bloqueia o agendador. Se uma rodada ainda está em andamento
 * (IA lenta), o disparo seguinte é descartado em vez de empilhar chamadas à IA.
 */
@Component
@Slf4j
public class PredictionCycleRunner {

    private final MapiService mapiService;
    private final Executor taskExecutor;
    private final ApplicationEventPublisher publisher;
    private final AtomicBoolean running = new AtomicBoolean(false);

    @Autowired
    public PredictionCycleRunner(MapiService mapiService, @Qualifier("taskExecutor") Executor taskExecutor,
                                 ApplicationEventPublisher publisher) {
        this.mapiService = mapiService;
        this.taskExecutor = taskExecutor;
        this.publisher = publisher;
    }

    /** Sem publicação de evento (testes do agendamento). */
    public PredictionCycleRunner(MapiService mapiService, Executor taskExecutor) {
        this(mapiService, taskExecutor, null);
    }

    // Evento de fim de ciclo (ADR 0011/0012): avaliador de alertas e stream SSE. Falha de ouvinte não propaga.
    private void publishCompleted(String origin) {
        if (publisher == null) return;
        try {
            publisher.publishEvent(new PredictionCycleCompletedEvent(origin, java.time.Instant.now()));
        } catch (Exception e) {
            log.error("Falha ao publicar o fim do ciclo [{}]: {}", origin, e.getMessage(), e);
        }
    }

    /** Agenda a rodada e retorna na hora. Devolve false se foi descartada (já rodando ou pool cheio). */
    public boolean triggerAfterCycle(String origin) {
        if (!running.compareAndSet(false, true)) {
            log.info("Predição do ciclo [{}] descartada: rodada anterior ainda em andamento", origin);
            return false;
        }
        try {
            taskExecutor.execute(() -> {
                MDC.put("requestId", "predict-" + origin);
                try {
                    mapiService.refreshPredictionsForActivePoints();
                    publishCompleted(origin);
                } catch (Exception e) {
                    log.error("Falha no passo de predição do ciclo [{}]: {}", origin, e.getMessage(), e);
                } finally {
                    running.set(false);
                    MDC.remove("requestId");
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("Pool taskExecutor cheio: predição do ciclo [{}] não agendada", origin);
            return false;
        }
    }
}
