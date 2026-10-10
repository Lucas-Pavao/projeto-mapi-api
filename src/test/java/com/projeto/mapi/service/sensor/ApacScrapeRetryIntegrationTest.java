package com.projeto.mapi.service.sensor;

import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Prova de ponta a ponta (com o proxy do Resilience4j ativo) de que a falha chega ao @Retry: com a
 * APAC inalcançável, o cliente tenta de novo e, ao esgotar as tentativas, LANÇA — em vez de devolver
 * lista vazia como o fallback antigo fazia (o que impedia qualquer retry).
 */
@SpringBootTest(properties = "app.apac.base-url=http://localhost:1")
@ActiveProfiles("test")
class ApacScrapeRetryIntegrationTest {

    @Autowired private ApacScrapeClient client;
    @Autowired private RetryRegistry retryRegistry;

    @Test
    void fonteInalcancavel_disparaRetryEPropagaAFalha() {
        assertThrows(ResourceAccessException.class, () -> client.fetchRawStations("cemaden"));

        var metrics = retryRegistry.retry("apacScrape").getMetrics();
        assertEquals(1, metrics.getNumberOfFailedCallsWithRetryAttempt());
        assertEquals(0, metrics.getNumberOfSuccessfulCallsWithoutRetryAttempt());
    }
}
