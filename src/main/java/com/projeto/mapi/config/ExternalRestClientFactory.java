package com.projeto.mapi.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ponto único de criação dos RestClient das fontes externas, sempre com timeout de conexão e de
 * leitura vindos de {@link HttpTimeoutProperties}. Usa o cliente HTTP do JDK, o mesmo que o
 * RestClient.builder() padrão já escolhia nesta classpath, então só muda o que é desejado: os timeouts.
 */
@Component
@RequiredArgsConstructor
public class ExternalRestClientFactory {

    private final HttpTimeoutProperties timeouts;
    private final Map<String, RestClient> cache = new ConcurrentHashMap<>();

    /** Builder com os timeouts da fonte; o chamador completa baseUrl/headers e chama build(). */
    public RestClient.Builder builder(String source) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(timeouts.connectTimeout(source))
                .withReadTimeout(timeouts.readTimeout(source));
        return RestClient.builder().requestFactory(ClientHttpRequestFactoryBuilder.jdk().build(settings));
    }

    /**
     * Cliente já pronto e reaproveitado por fonte, para serviços que montam o RestClient sob demanda
     * (baseUrl e headers fixos por fonte).
     */
    public RestClient client(String source, String baseUrl, Map<String, String> defaultHeaders) {
        return cache.computeIfAbsent(source, s -> {
            RestClient.Builder b = builder(s).baseUrl(baseUrl);
            defaultHeaders.forEach(b::defaultHeader);
            return b.build();
        });
    }
}
