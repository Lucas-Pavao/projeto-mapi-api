package com.projeto.mapi.service.impl;

import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.service.FloodPredictionService;
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
            return restClient.post()
                    .uri("/v1/predict/flood")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FloodPredictionResponseDTO.class);
        } catch (Exception e) {
            log.error("Erro ao chamar API de IA: {}", e.getMessage());
            return FloodPredictionResponseDTO.builder()
                    .floodProbability(0.0)
                    .riskLevel("UNKNOWN")
                    .message("Erro na comunicação com o modelo de IA: " + e.getMessage())
                    .build();
        }
    }
}
