package com.projeto.mapi.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Cliente REST da ANA (Agência Nacional de Águas), porta do coletor Python de referência
 * (projeto-mapi/src/collectors/ana_rest_collector.py + services/auth_manager.py).
 */
public interface AnaDataClient {
    /**
     * Busca as leituras telemétricas de uma estação, já enriquecidas com os metadados de
     * inventário (Latitude/Longitude/Estacao_Nome/Bacia_Nome/Municipio_Nome).
     */
    List<JsonNode> fetchStationMeasurements(String stationCode, String searchDate);
}
