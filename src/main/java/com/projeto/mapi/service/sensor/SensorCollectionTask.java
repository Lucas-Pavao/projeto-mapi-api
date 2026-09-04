package com.projeto.mapi.service.sensor;

import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.sensor.ana.AnaCollectorService;
import com.projeto.mapi.service.sensor.apac.ApacCollectorService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Substitui o antigo fluxo MQTT: em vez de um processo Python externo publicar dados de sensores
 * num broker, a própria API busca periodicamente os dados reais da ANA e da APAC e os processa
 * pelo mesmo pipeline de negócio de sempre (SensorService.processSensorMessage).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorCollectionTask {

    private final AnaCollectorService anaCollectorService;
    private final ApacCollectorService apacCollectorService;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedRateString = "${app.ana.fixed-rate-ms:900000}",
            initialDelayString = "${app.ana.initial-delay-ms:15000}")
    public void collectAna() {
        runCycle("ana-job", "ANA", "ana", anaCollectorService::collectAll);
    }

    @Scheduled(fixedRateString = "${app.apac.cemaden-fixed-rate-ms:180000}",
            initialDelayString = "${app.apac.initial-delay-ms:20000}")
    public void collectApacCemaden() {
        runCycle("apac-cemaden-job", "APAC/Cemaden", "apac_cemaden", apacCollectorService::collectCemaden);
    }

    @Scheduled(fixedRateString = "${app.apac.meteorologia-fixed-rate-ms:300000}",
            initialDelayString = "${app.apac.initial-delay-ms:25000}")
    public void collectApacMeteorologia() {
        runCycle("apac-meteo-job", "APAC/Meteorologia24h", "apac_meteorologia", apacCollectorService::collectMeteorologia24h);
    }

    // "source" (terceiro parâmetro) é a versão em slug do "label", usada como tag de métrica —
    // Prometheus não aceita "/" ou espaço em valores de label com segurança de query, por isso
    // não reaproveitamos o label legível dos logs diretamente.
    private void runCycle(String jobPrefix, String label, String source, Supplier<CollectionSummaryDTO> action) {
        String correlationId = jobPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", correlationId);
        long start = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            log.info(">> Iniciando coleta agendada [{}]", label);
            CollectionSummaryDTO summary = action.get();
            meterRegistry.counter("mapi.collector.records.processed", "source", source).increment(summary.processed());
            meterRegistry.counter("mapi.collector.records.errors", "source", source).increment(summary.errors());
            log.info("<< Coleta [{}] concluída: processados={} erros={} tempo={}ms",
                    label, summary.processed(), summary.errors(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            outcome = "failure";
            meterRegistry.counter("mapi.collector.failures", "source", source).increment();
            log.error("Falha inesperada na coleta agendada [{}]: {}", label, e.getMessage(), e);
        } finally {
            sample.stop(Timer.builder("mapi.collector.run")
                    .description("Duração de cada ciclo de coleta agendada (ANA/APAC)")
                    .tag("source", source)
                    .tag("outcome", outcome)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            MDC.remove("requestId");
        }
    }
}
