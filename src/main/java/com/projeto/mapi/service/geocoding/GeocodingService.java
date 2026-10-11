package com.projeto.mapi.service.geocoding;

import java.util.Optional;

public interface GeocodingService {
    Optional<double[]> geocode(String address, String neighborhood, String city);

    /**
     * Como {@link #geocode}, mas com o nível de precisão do resultado. Implementações que não sabem o nível
     * devolvem {@code level == null} (não se inventa).
     */
    default Optional<GeocodeResult> geocodeWithLevel(String address, String neighborhood, String city) {
        return geocode(address, neighborhood, city).map(c -> new GeocodeResult(c[0], c[1], null));
    }
}
