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

    /**
     * Previsão indisponível por falta de dado, decidida pelo chamador sem consultar a IA (regra de degradação da chuva
     * medida, ADR 0007 emenda 3): {@code UNKNOWN} com {@code INSUFFICIENT_DATA} e as entradas ausentes declaradas.
     */
    PredictionResult insufficientData(Collection<String> absentFeatureIds);

    default FloodPredictionResponseDTO getPrediction(FloodPredictionRequestDTO request) {
        return predict(request, List.of()).response();
    }
}
