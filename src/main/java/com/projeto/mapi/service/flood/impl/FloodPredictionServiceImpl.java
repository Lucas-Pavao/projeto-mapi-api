package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.service.flood.FloodPredictionService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.http.MediaType;

@Service
@Slf4j
public class FloodPredictionServiceImpl implements FloodPredictionService {

    private final RestClient restClient;

    public FloodPredictionServiceImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(aiApiUrl)
                .build();
    }

    @Override
    public FloodPredictionResponseDTO getPrediction(FloodPredictionRequestDTO request) {
        log.info("Solicitando predição de alagamento para estação: {}", request.getStationId());

        try {
            AiPredictionDTO aiResponse = restClient.post()
                    .uri("/v1/predict/flood")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiPredictionDTO.class);

            if (aiResponse == null) {
                return unknownResult("Resposta vazia do modelo de IA");
            }

            return FloodPredictionResponseDTO.builder()
                    .floodProbability(aiResponse.floodProbability)
                    .riskLevel(aiResponse.riskLevel)
                    .estimatedTimeToEvent(aiResponse.estimatedTimeToEvent)
                    .message(aiResponse.message)
                    .build();
        } catch (Exception e) {
            log.error("Erro ao chamar API de IA: {}", e.getMessage());
            return unknownResult("Erro na comunicação com o modelo de IA: " + e.getMessage());
        }
    }

    private FloodPredictionResponseDTO unknownResult(String message) {
        return FloodPredictionResponseDTO.builder()
                .floodProbability(0.0)
                .riskLevel("UNKNOWN")
                .message(message)
                .build();
    }

    // Shape bruto devolvido pela MAPI AI (Python/FastAPI, snake_case) — só usado aqui, pra
    // desserializar a resposta HTTP antes de converter pro contrato limpo (camelCase) que a nossa
    // própria API expõe em FloodPredictionResponseDTO.
    @Data
    private static class AiPredictionDTO {
        @JsonProperty("flood_probability")
        private Double floodProbability;

        @JsonProperty("risk_level")
        private String riskLevel;

        @JsonProperty("estimated_time_to_event")
        private String estimatedTimeToEvent;

        private String message;
    }
}
