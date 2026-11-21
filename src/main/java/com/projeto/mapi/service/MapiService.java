package com.projeto.mapi.service;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import java.util.List;

public interface MapiService {
    // Composição interna de dado ambiental + ÚLTIMA previsão gravada, usada pela ficha de um ponto
    // monitorado (getFloodPointBySlug/getAllFloodPoints). Leitura pura (ADR 0007 §7): não chama a
    // MAPI AI e não grava em flood_predictions.
    // Não tem endpoint HTTP dedicado: quem precisa de dado ambiental avulso ou de uma predição
    // avulsa usa getEnvironmentalData.
    MapiResponseDTO getPreciseData(double latitude, double longitude);

    // Dado ambiental agregado (sensores, clima, maré, ondas) para uma coordenada — leitura pura,
    // sem chamar a MAPI AI nem gravar auditoria. Seguro para polling frequente (ex: mapa em tempo
    // real): GET /api/environmental-data.
    MapiResponseDTO getEnvironmentalData(double latitude, double longitude);

    // Passo do ciclo de coleta: previsão de chuva (rain_v1) de cada ponto ativo, gravada para conferência. Devolve
    // quantos pontos foram processados. O classificador de alagamento saiu em 21/11/2026 (prompt de 20/11).
    int refreshRainForecastsForActivePoints();

    FloodPointResponseDTO createFloodPoint(FloodPointRequestDTO request);
    List<FloodPointResponseDTO> getAllFloodPoints();
    FloodPointResponseDTO getFloodPointBySlug(String slug);
    void seedPilotData();
    com.projeto.mapi.dto.FloodScenarioLabelResponseDTO registerScenarioLabel(com.projeto.mapi.dto.FloodScenarioLabelRequestDTO request);
}
