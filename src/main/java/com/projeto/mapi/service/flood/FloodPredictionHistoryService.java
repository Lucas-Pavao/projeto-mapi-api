package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;

import java.time.Instant;
import java.util.Optional;

/** Auditoria de previsões em {@code flood_predictions} e leitura da última previsão de um ponto. */
public interface FloodPredictionHistoryService {

    /** Grava a previsão (floodPointId nulo no POST de cenário) (inclusive {@code UNKNOWN}). Falha de gravação é registrada e não propaga. */
    void record(FloodPredictionRequestDTO request, PredictionResult result, Long floodPointId);

    /**
     * Última previsão gravada do ponto (por {@code flood_point_id}; sem linhas por id, cai na busca por coordenada), já no contrato do ADR 0007, com
     * {@code lastValidIssuedAt} preenchido. Não chama a IA e não grava nada.
     */
    Optional<FloodPredictionResponseDTO> findLatest(Long floodPointId, double latitude, double longitude);

    /** {@code issuedAt} da última previsão SUCCESS da coordenada, ou vazio se nunca houve. */
    Optional<java.time.Instant> findLastValidIssuedAt(Long floodPointId, double latitude, double longitude);

    /**
     * Previsões do ponto com {@code issuedAt} em [from, to), ordenadas, já no contrato do ADR 0007. Não chama a IA e
     * não grava. {@code lastValidIssuedAt} de cada item é a última previsão válida até ele (inclusive).
     */
    java.util.List<FloodPredictionResponseDTO> findSeries(Long floodPointId, Instant from, Instant to);
}
