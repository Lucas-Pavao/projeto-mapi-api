package com.projeto.mapi.service.sensor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.config.HttpTimeoutProperties;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.sensor.apac.impl.ApacScrapeClientImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * O cliente da APAC não pode mais transformar falha em lista vazia: erro HTTP, corpo vazio e corpo
 * que não é JSON viram exceção; só uma lista JSON vazia de verdade devolve "0 itens".
 */
class ApacScrapeClientFailureTest {

    private MockRestServiceServer server;
    private ApacScrapeClientImpl client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        // Fábrica real, mas devolvendo o builder já ligado ao servidor simulado.
        ExternalRestClientFactory http = new ExternalRestClientFactory(new HttpTimeoutProperties()) {
            @Override
            public RestClient.Builder builder(String source) {
                return builder;
            }
        };
        client = new ApacScrapeClientImpl(http, new AppProperties(), new ObjectMapper());
    }

    @Test
    void erro5xx_propagaParaORetry() {
        server.expect(method(HttpMethod.GET)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThrows(HttpServerErrorException.class, () -> client.fetchRawStations("cemaden"));
    }

    @Test
    void corpoVazio_naoEhZeroItens() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("   ", MediaType.TEXT_HTML));
        assertThrows(SourceUnavailableException.class, () -> client.fetchRawStations("cemaden"));
    }

    @Test
    void corpoQueNaoEhJson_naoEhZeroItens() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("<html><body>erro interno</body></html>", MediaType.TEXT_HTML));
        assertThrows(SourceUnavailableException.class, () -> client.fetchRawStations("cemaden"));
    }

    @Test
    void listaJsonVazia_eZeroItensLegitimo() {
        server.expect(method(HttpMethod.GET)).andRespond(withSuccess("<html><body>[]</body></html>", MediaType.TEXT_HTML));
        assertEquals(0, client.fetchRawStations("cemaden").size());
    }
}
