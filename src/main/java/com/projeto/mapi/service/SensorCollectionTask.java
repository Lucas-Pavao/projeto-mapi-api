package com.projeto.mapi.service;

import com.projeto.mapi.dto.CollectionSummaryDTO;
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

    @Scheduled(fixedRateString = "${app.ana.fixed-rate-ms:900000}",
            initialDelayString = "${app.ana.initial-delay-ms:15000}")
    public void collectAna() {
        runCycle("ana-job", "ANA", anaCollectorService::collectAll);
    }

    @Scheduled(fixedRateString = "${app.apac.cemaden-fixed-rate-ms:180000}",
            initialDelayString = "${app.apac.initial-delay-ms:20000}")
    public void collectApacCemaden() {
        runCycle("apac-cemaden-job", "APAC/Cemaden", apacCollectorService::collectCemaden);
    }

    @Scheduled(fixedRateString = "${app.apac.meteorologia-fixed-rate-ms:300000}",
            initialDelayString = "${app.apac.initial-delay-ms:25000}")
    public void collectApacMeteorologia() {
        runCycle("apac-meteo-job", "APAC/Meteorologia24h", apacCollectorService::collectMeteorologia24h);
    }

    private void runCycle(String jobPrefix, String label, Supplier<CollectionSummaryDTO> action) {
        String correlationId = jobPrefix + "-" + UUID.randomUUID().toString().substring(0, 8);
        MDC.put("requestId", correlationId);
        long start = System.currentTimeMillis();
        try {
            log.info(">> Iniciando coleta agendada [{}]", label);
            CollectionSummaryDTO summary = action.get();
            log.info("<< Coleta [{}] concluída: processados={} erros={} tempo={}ms",
                    label, summary.processed(), summary.errors(), System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.error("Falha inesperada na coleta agendada [{}]: {}", label, e.getMessage(), e);
        } finally {
            MDC.remove("requestId");
        }
    }
}
