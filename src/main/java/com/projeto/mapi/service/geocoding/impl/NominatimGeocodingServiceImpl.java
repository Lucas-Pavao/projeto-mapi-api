package com.projeto.mapi.service.geocoding.impl;

import com.projeto.mapi.config.ExternalRestClientFactory;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.service.geocoding.GeocodeLevel;
import com.projeto.mapi.service.geocoding.GeocodeResult;
import com.projeto.mapi.service.geocoding.GeocodingService;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@lombok.RequiredArgsConstructor
@Slf4j
public class NominatimGeocodingServiceImpl implements GeocodingService {

    private final Map<String, Optional<GeocodeResult>> cache = new ConcurrentHashMap<>();

    private final ExternalRestClientFactory http;

    // Cliente criado sob demanda e reaproveitado por fonte, com os timeouts de app.http.clients.nominatim
    private RestClient restClient() {
        return http.client("nominatim", "https://nominatim.openstreetmap.org", Map.of("User-Agent", "MAPI-Project-Recife"));
    }

    @Override
    public Optional<double[]> geocode(String address, String neighborhood, String city) {
        return geocodeWithLevel(address, neighborhood, city).map(r -> new double[]{r.lat(), r.lon()});
    }

    @Override
    public Optional<GeocodeResult> geocodeWithLevel(String address, String neighborhood, String city) {
        boolean hasAddress = address != null && !address.isBlank();
        boolean hasNeighborhood = neighborhood != null && !neighborhood.isBlank();
        String mainQuery = String.format("%s, %s, %s, Pernambuco, Brazil", hasAddress ? address : "",
                hasNeighborhood ? neighborhood : "", city);

        if (cache.containsKey(mainQuery)) {
            log.debug("[Cache] Hit para endereço: {}", mainQuery);
            return cache.get(mainQuery);
        }

        Optional<GeocodeResult> result = Optional.empty();
        // Falha de rede/HTTP não é "endereço inexistente": nesse caso o resultado vazio NÃO entra no cache.
        boolean[] transientFailure = {false};

        // Nível 1: endereço completo com bairro (só se há endereço)
        if (hasAddress) {
            result = fetchFromNominatim(mainQuery, GeocodeLevel.STREET, transientFailure);
        }

        // Nível 2: apenas endereço e cidade (caso o bairro esteja com nome divergente)
        if (result.isEmpty() && hasAddress) {
            String fallbackQuery = String.format("%s, %s, Pernambuco, Brazil", address, city);
            log.info("[Geocode] Falha no nível 1. Tentando Fallback Nível 2: {}", fallbackQuery);
            result = fetchFromNominatim(fallbackQuery, GeocodeLevel.STREET, transientFailure);
        }

        // Nível 3: apenas bairro e cidade (último recurso para manter estatística regional)
        if (result.isEmpty() && hasNeighborhood) {
            String neighborhoodQuery = String.format("%s, %s, Pernambuco, Brazil", neighborhood, city);
            log.info("[Geocode] Falha no nível 2. Tentando Fallback Nível 3 (Bairro): {}", neighborhoodQuery);
            result = fetchFromNominatim(neighborhoodQuery, GeocodeLevel.NEIGHBORHOOD, transientFailure);
        }

        if (result.isPresent() || !transientFailure[0]) cache.put(mainQuery, result);
        return result;
    }

    private static final Set<String> CITY_TYPES = Set.of("city", "town", "municipality", "county", "state_district",
            "state", "region", "country", "province");
    private static final Set<String> AREA_TYPES = Set.of("suburb", "neighbourhood", "neighborhood", "quarter",
            "city_district", "district", "borough", "village", "hamlet", "locality", "residential_area", "subdivision");

    /**
     * Nível do resultado pelo tipo do objeto que o Nominatim devolveu ({@code addresstype}, senão {@code type}).
     * Sem tipo reconhecível, vale o nível da consulta que o encontrou (níveis 1 e 2: rua; nível 3: bairro).
     */
    static GeocodeLevel levelOf(String addressType, String type, String osmClass, GeocodeLevel queryLevel) {
        String t = addressType != null && !addressType.isBlank() ? addressType : type;
        if (t != null && !t.isBlank()) {
            String k = t.toLowerCase(Locale.ROOT);
            if (CITY_TYPES.contains(k)) return GeocodeLevel.CITY;
            if (AREA_TYPES.contains(k)) return GeocodeLevel.NEIGHBORHOOD;
            // qualquer outro objeto que não seja área administrativa/lugar (via, edificação, comércio...) é pontual
            if (osmClass != null && !osmClass.equalsIgnoreCase("boundary") && !osmClass.equalsIgnoreCase("place")) {
                return GeocodeLevel.STREET;
            }
            if ("road".equals(k) || "house".equals(k) || "building".equals(k)) return GeocodeLevel.STREET;
        }
        return queryLevel;
    }

    private Optional<GeocodeResult> fetchFromNominatim(String query, GeocodeLevel queryLevel, boolean[] transientFailure) {
        int retries = 3;
        while (retries > 0) {
            try {
                // Respeitar o rate limit do Nominatim (1 req/sec)
                Thread.sleep(1100);

                List<NominatimResult> results = restClient().get()
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
                    return Optional.of(new GeocodeResult(Double.parseDouble(res.getLat()), Double.parseDouble(res.getLon()),
                            levelOf(res.getAddresstype(), res.getType(), res.getOsmClass(), queryLevel)));
                }
                return Optional.empty(); // Se retornou lista vazia, o endereço não existe mesmo
            } catch (Exception e) {
                retries--;
                log.warn("[Geocode] Erro na consulta (Tentativas restantes: {}): {}. Mensagem: {}",
                    retries, query, e.getMessage());
                if (retries == 0) {
                    log.error("[Geocode] Falha definitiva para query: {}", query);
                    transientFailure[0] = true;
                }
                try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
            }
        }
        return Optional.empty();
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NominatimResult {
        private String lat;
        private String lon;
        private String type;
        private String addresstype;
        @JsonProperty("class")
        private String osmClass;
    }
}
