package com.projeto.mapi.service.sensor.apac.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Porta do coletor de scraping da APAC (projeto-mapi/src/collectors/base_collector.py). A base
 * hoje é HTTP puro (não HTTPS), então não há verificação de TLS a desabilitar — diferente do
 * Python original, que usava verify=False; se algum dia a APAC migrar para HTTPS, este client
 * usa a verificação padrão da JVM (correção de segurança pedida na migração).
 */
@Service
@Slf4j
public class ApacScrapeClientImpl implements ApacScrapeClient {

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public ApacScrapeClientImpl(RestClient.Builder restClientBuilder, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder
                .baseUrl(appProperties.getApac().getBaseUrl())
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "apacScrape")
    @CircuitBreaker(name = "apacScrape", fallbackMethod = "fetchRawStationsFallback")
    public List<JsonNode> fetchRawStations(String endpointPath) {
        String html = restClient.get()
                .uri("/{endpoint}/", endpointPath)
                .retrieve()
                .body(String.class);

        if (html == null || html.isBlank()) {
            return List.of();
        }

        String jsonText = Jsoup.parse(html).body().text();
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            List<JsonNode> result = new ArrayList<>();
            if (root.isArray()) {
                root.forEach(result::add);
            }
            return result;
        } catch (Exception e) {
            log.warn("Resposta da APAC ({}) não é um JSON válido: {}", endpointPath, e.getMessage());
            return List.of();
        }
    }

    private List<JsonNode> fetchRawStationsFallback(String endpointPath, Throwable t) {
        log.warn("Falha ao coletar dados da APAC ({}): {} — {}", endpointPath, t.getClass().getSimpleName(), t.getMessage());
        return List.of();
    }
}
