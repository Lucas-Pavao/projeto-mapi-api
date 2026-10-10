package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Timeouts dos clientes HTTP das fontes externas (app.http.* no application.yml). Sem timeout, uma
 * fonte que aceita a conexão e nunca responde prende a thread para sempre (a de coleta agendada, a
 * de ingestão ou, no caso da IA, a requisição do usuário). Cada fonte pode sobrescrever o padrão
 * em {@code app.http.clients.<fonte>}; o que não for informado herda de {@code app.http.default-*}.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "app.http")
public class HttpTimeoutProperties {

    private Duration defaultConnectTimeout = Duration.ofSeconds(5);
    private Duration defaultReadTimeout = Duration.ofSeconds(15);
    private Map<String, Client> clients = new HashMap<>();

    @Data
    public static class Client {
        private Duration connectTimeout;
        private Duration readTimeout;
    }

    public Duration connectTimeout(String source) {
        Client c = clients.get(source);
        return c != null && c.getConnectTimeout() != null ? c.getConnectTimeout() : defaultConnectTimeout;
    }

    public Duration readTimeout(String source) {
        Client c = clients.get(source);
        return c != null && c.getReadTimeout() != null ? c.getReadTimeout() : defaultReadTimeout;
    }
}
