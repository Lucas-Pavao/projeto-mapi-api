package com.projeto.mapi.service.sensor.apac.impl;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.exception.SourceUnavailableException;
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

    public ApacScrapeClientImpl(ExternalRestClientFactory http, AppProperties appProperties, ObjectMapper objectMapper) {
        this.restClient = http.builder("apac")
                .baseUrl(appProperties.getApac().getBaseUrl())
                .defaultHeader("User-Agent", USER_AGENT)
                .defaultHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .build();
        this.objectMapper = objectMapper;
    }

    @Override
    @Retry(name = "apacScrape")
    // Sem fallbackMethod de propósito: o fallback antigo devolvia List.of(), então a falha virava
    // "sucesso com 0 itens" e o @Retry (mais externo) nunca via a exceção. Agora a exceção sobe:
    // ResourceAccessException/5xx são retentadas e, esgotadas as tentativas (ou com o circuito
    // aberto), chegam ao coletor, que marca o ciclo como "fonte indisponível".
    @CircuitBreaker(name = "apacScrape")
    public List<JsonNode> fetchRawStations(String endpointPath) {
        String html = restClient.get()
                .uri("/{endpoint}/", endpointPath)
                .retrieve()
                .body(String.class);

        if (html == null || html.isBlank()) {
            throw new SourceUnavailableException("Resposta vazia da APAC (" + endpointPath + ")");
        }

        String jsonText = Jsoup.parse(html).body().text();
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            List<JsonNode> result = new ArrayList<>();
            if (!root.isArray()) {
                throw new SourceUnavailableException("Resposta da APAC (" + endpointPath + ") não é uma lista JSON");
            }
            root.forEach(result::add);
            return result;
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Resposta da APAC ({}) não é um JSON válido: {}", endpointPath, e.getMessage());
            throw new SourceUnavailableException("Resposta da APAC (" + endpointPath + ") não é um JSON válido", e);
        }
    }
}
