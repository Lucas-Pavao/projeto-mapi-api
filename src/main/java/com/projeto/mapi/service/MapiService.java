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
    // avulsa usa getEnvironmentalData/createFloodPrediction diretamente.
    MapiResponseDTO getPreciseData(double latitude, double longitude);

    // Dado ambiental agregado (sensores, clima, maré, ondas) para uma coordenada — leitura pura,
    // sem chamar a MAPI AI nem gravar auditoria. Seguro para polling frequente (ex: mapa em tempo
    // real): GET /api/environmental-data.
    MapiResponseDTO getEnvironmentalData(double latitude, double longitude);

    // Roda uma nova avaliação de risco de alagamento pra uma coordenada: chama a MAPI AI e grava
    // o resultado em flood_predictions. Não é idempotente (cada chamada gera uma nova linha de
    // auditoria) e tem efeito colateral (chamada HTTP externa + escrita em banco) — por isso é
    // exposto como POST /api/flood-predictions (cria um novo registro de predição), não GET.
    // Reaproveita o mesmo dado ambiental de getEnvironmentalData internamente.
    com.projeto.mapi.dto.FloodPredictionResponseDTO createFloodPrediction(double latitude, double longitude);

    // Passo de predição do ciclo de coleta (ADR 0007 §7): uma previsão por ponto ativo, gravada em
    // flood_predictions. Devolve quantos pontos foram processados.
    int refreshPredictionsForActivePoints();

    FloodPointResponseDTO createFloodPoint(FloodPointRequestDTO request);
    List<FloodPointResponseDTO> getAllFloodPoints();
    FloodPointResponseDTO getFloodPointBySlug(String slug);
    void seedPilotData();
    com.projeto.mapi.dto.FloodScenarioLabelResponseDTO registerScenarioLabel(com.projeto.mapi.dto.FloodScenarioLabelRequestDTO request);
}
