package com.projeto.mapi.service.warning;

import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.exception.SourceUnavailableException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/** Adaptador HTTP do INMET com timeout explícito, User-Agent de navegador e Resilience4j. */
@Component
@RequiredArgsConstructor
public class InmetWarningClientImpl implements InmetWarningClient {

    private final ExternalRestClientFactory restFactory;
    private final AppProperties props;

    @Override
    @CircuitBreaker(name = "inmet")
    @Retry(name = "inmet")
    public String fetchActiveJson() {
        AppProperties.Inmet cfg = props.getInmet();
        try {
            String body = restFactory
                    .client("inmet", cfg.getBaseUrl(), Map.of(HttpHeaders.USER_AGENT, cfg.getUserAgent()))
                    .get()
                    .uri(cfg.getActivePath())
                    .retrieve()
                    .body(String.class);
            if (body == null || body.isBlank()) {
                throw new SourceUnavailableException("INMET retornou corpo vazio em " + cfg.getActivePath());
            }
            return body;
        } catch (RestClientException e) {
            throw new SourceUnavailableException("Falha ao consultar avisos do INMET: " + e.getMessage(), e);
        }
    }
}
