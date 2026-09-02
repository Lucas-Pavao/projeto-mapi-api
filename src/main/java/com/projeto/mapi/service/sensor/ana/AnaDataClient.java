package com.projeto.mapi.service.sensor.ana;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.dto.AnaStationDTO;

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

    /**
     * Descobre dinamicamente as estações telemétricas ativas dentro do raio configurado (ver
     * AppProperties.Ana), via o serviço legado público de inventário da ANA. Resultado cacheado
     * em memória (TTL configurável) para não bater no serviço a cada ciclo de coleta.
     */
    List<AnaStationDTO> discoverStations();
}
