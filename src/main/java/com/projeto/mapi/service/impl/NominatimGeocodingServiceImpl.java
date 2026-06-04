package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.projeto.mapi.service.GeocodingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class NominatimGeocodingServiceImpl implements GeocodingService {

    private final Map<String, Optional<double[]>> cache = new ConcurrentHashMap<>();
    
    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://nominatim.openstreetmap.org")
            .defaultHeader("User-Agent", "MAPI-Project-Recife")
            .build();

    @Override
    public Optional<double[]> geocode(String address, String neighborhood, String city) {
        String mainQuery = String.format("%s, %s, %s, Pernambuco, Brazil", address, neighborhood, city);
        
        if (cache.containsKey(mainQuery)) {
            log.debug("[Cache] Hit para endereço: {}", mainQuery);
            return cache.get(mainQuery);
        }

        // Tentar Nível 1: Endereço completo com Bairro
        Optional<double[]> result = fetchFromNominatim(mainQuery);
        
        // Tentar Nível 2: Apenas Endereço e Cidade (Caso o bairro esteja com nome divergente)
        if (result.isEmpty() && address != null && !address.isBlank()) {
            String fallbackQuery = String.format("%s, %s, Pernambuco, Brazil", address, city);
            log.info("[Geocode] Falha no nível 1. Tentando Fallback Nível 2: {}", fallbackQuery);
            result = fetchFromNominatim(fallbackQuery);
        }

        // Tentar Nível 3: Apenas Bairro e Cidade (Último recurso para manter estatística regional)
        if (result.isEmpty() && neighborhood != null && !neighborhood.isBlank()) {
            String neighborhoodQuery = String.format("%s, %s, Pernambuco, Brazil", neighborhood, city);
            log.info("[Geocode] Falha no nível 2. Tentando Fallback Nível 3 (Bairro): {}", neighborhoodQuery);
            result = fetchFromNominatim(neighborhoodQuery);
        }

        cache.put(mainQuery, result);
        return result;
    }

    private Optional<double[]> fetchFromNominatim(String query) {
        try {
            // Respeitar o rate limit do Nominatim (1 req/sec)
            Thread.sleep(1000);

            List<NominatimResult> results = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "json")
                            .queryParam("limit", 1)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<NominatimResult>>() {});

            if (results != null && !results.isEmpty()) {
                NominatimResult res = results.get(0);
                return Optional.of(new double[]{
                    Double.parseDouble(res.getLat()), 
                    Double.parseDouble(res.getLon())
                });
            }
        } catch (Exception e) {
            log.error("Erro ao consultar Nominatim para query: {}", query, e);
        }
        return Optional.empty();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResult {
        private String lat;
        private String lon;
    }
}
