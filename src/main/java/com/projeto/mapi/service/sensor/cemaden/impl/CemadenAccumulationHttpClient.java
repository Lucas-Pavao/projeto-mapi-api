package com.projeto.mapi.service.sensor.cemaden.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.exception.SourceUnavailableException;
import com.projeto.mapi.service.sensor.cemaden.CemadenAccumulationClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** HTTP do painel da CEMADEN (timeouts da fonte "cemaden"). A resposta vem como text/html, mas o corpo é JSON. */
@Component
@RequiredArgsConstructor
public class CemadenAccumulationHttpClient implements CemadenAccumulationClient {

    private final ExternalRestClientFactory http;
    private final AppProperties appProperties;
    private final ObjectMapper mapper;

    @Override
    public List<JsonNode> fetchState(String uf) {
        String base = appProperties.getCemaden().getBaseUrl();
        String url = base + "/graficos/interativo/getJson2.php?uf=" + uf;
        try {
            String body = http.client("cemaden", base, Map.of()).get().uri(URI.create(url)).retrieve().body(String.class);
            if (body == null || body.isBlank()) throw new SourceUnavailableException("CEMADEN: corpo vazio em " + url);
            JsonNode root = mapper.readTree(body);
            if (!root.isArray()) throw new SourceUnavailableException("CEMADEN: resposta não é lista em " + url);
            List<JsonNode> out = new ArrayList<>();
            root.forEach(out::add);
            return out;
        } catch (SourceUnavailableException e) {
            throw e;
        } catch (Exception e) {
            throw new SourceUnavailableException("CEMADEN indisponível (" + url + "): " + e.getMessage(), e);
        }
    }
}
