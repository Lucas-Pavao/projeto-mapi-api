package com.projeto.mapi.service.alerta;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Disparo do ciclo do alerta a cada coleta ao vivo (item 2.2), no {@code taskExecutor}, separado do ciclo de previsão
 * de alagamento: um ciclo de alagamento lento não segura o alerta. Rodada em andamento descarta o disparo seguinte.
 */
@Component
@Slf4j
public class AlertaCycleRunner {

    private final AlertaCycleService service;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public AlertaCycleRunner(AlertaCycleService service, @Qualifier("taskExecutor") Executor executor) {
        this.service = service;
        this.executor = executor;
    }

    public boolean trigger(String origin) {
        if (!running.compareAndSet(false, true)) {
            log.info("Ciclo do alerta [{}] descartado: anterior em andamento", origin);
            return false;
        }
        try {
            executor.execute(() -> {
                MDC.put("requestId", "alerta-" + origin);
                try {
                    AlertaCycleService.CycleSummary s = service.run(origin);
                    log.info("Ciclo do alerta [{}]: {} ponto(s), balde {} ms", origin, s.points(), Math.round(s.bucketMsTotal()));
                } catch (Exception e) {
                    log.error("Falha no ciclo do alerta [{}]: {}", origin, e.getMessage(), e);
                } finally {
                    running.set(false);
                    MDC.remove("requestId");
                }
            });
            return true;
        } catch (RejectedExecutionException e) {
            running.set(false);
            log.warn("Pool cheio: ciclo do alerta [{}] não agendado", origin);
            return false;
        }
    }
}
