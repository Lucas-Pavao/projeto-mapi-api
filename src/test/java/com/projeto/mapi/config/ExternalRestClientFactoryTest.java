package com.projeto.mapi.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalRestClientFactoryTest {

    private HttpServer server;

    @BeforeEach
    void sobeServidorLento() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Aceita a conexão e nunca responde a tempo: o cenário que prendia a thread para sempre.
        server.createContext("/lento", ex -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) { }
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        server.start();
    }

    @AfterEach
    void paraServidor() {
        server.stop(0);
    }

    @Test
    void leituraEstouraNoTimeoutConfigurado() {
        HttpTimeoutProperties props = new HttpTimeoutProperties();
        HttpTimeoutProperties.Client c = new HttpTimeoutProperties.Client();
        c.setReadTimeout(Duration.ofMillis(300));
        props.getClients().put("lenta", c);
        RestClient client = new ExternalRestClientFactory(props)
                .client("lenta", "http://127.0.0.1:" + server.getAddress().getPort(), java.util.Map.of());

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThrows(ResourceAccessException.class,
                        () -> client.get().uri("/lento").retrieve().body(String.class)));
    }

    @Test
    void fonteSemEntradaHerdaOPadrao() {
        HttpTimeoutProperties props = new HttpTimeoutProperties();
        assertEquals(Duration.ofSeconds(5), props.connectTimeout("qualquer"));
        assertEquals(Duration.ofSeconds(15), props.readTimeout("qualquer"));

        HttpTimeoutProperties.Client parcial = new HttpTimeoutProperties.Client();
        parcial.setReadTimeout(Duration.ofSeconds(8));
        props.getClients().put("ai", parcial);
        assertEquals(Duration.ofSeconds(8), props.readTimeout("ai"));
        assertTrue(props.connectTimeout("ai").equals(Duration.ofSeconds(5)), "conexão herda o padrão");
    }
}
