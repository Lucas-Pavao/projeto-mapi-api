package com.projeto.mapi.service.alagamento.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.service.alagamento.AiHealthClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
@Slf4j
public class AiHealthClientImpl implements AiHealthClient {

    private final RestClient restClient;

    @Autowired
    public AiHealthClientImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl, ExternalRestClientFactory http) {
        this(http.builder("ai").baseUrl(aiApiUrl).build());
    }

    public AiHealthClientImpl(RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public JsonNode health() {
        try {
            return restClient.get().uri("/health").retrieve().body(JsonNode.class);
        } catch (Exception e) {
            log.warn("MAPI AI /health indisponível: {}", e.toString());
            return null;
        }
    }
}
