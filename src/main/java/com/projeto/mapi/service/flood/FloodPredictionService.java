package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;

import java.util.Collection;
import java.util.List;

public interface FloodPredictionService {

    /**
     * Pede a previsão à IA e traduz a resposta para o contrato do ADR 0007. Nunca lança: toda falha
     * vira {@code UNKNOWN} com {@code degradationReason} (§4).
     *
     * @param imputedFeatureIds ids das variáveis que o chamador preencheu por imputação ao montar o
     *                          request; vão para {@code imputedFeatures} da resposta
     */
    PredictionResult predict(FloodPredictionRequestDTO request, Collection<String> imputedFeatureIds);

    default FloodPredictionResponseDTO getPrediction(FloodPredictionRequestDTO request) {
        return predict(request, List.of()).response();
    }
}
