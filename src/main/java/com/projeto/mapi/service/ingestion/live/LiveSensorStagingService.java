package com.projeto.mapi.service.ingestion.live;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Ponte dos coletores ao vivo (ANA, APAC, Cemaden) para o pipeline do ADR 0006, sem desligar o fluxo legado
 * (SensorService.processSensorMessage continua gravando em sensor_data).
 */
public interface LiveSensorStagingService {

    /**
     * Grava o lote de envelopes no staging da fonte e valida. Nunca lança exceção: o fluxo legado não pode
     * depender do pipeline novo. Devolve quantos registros foram entregues ao pipeline (0 em falha).
     */
    int stage(String source, List<JsonNode> envelopes);
}
