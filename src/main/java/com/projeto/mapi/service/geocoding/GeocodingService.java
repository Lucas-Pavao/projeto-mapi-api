package com.projeto.mapi.service.geocoding;

import java.util.Optional;

public interface GeocodingService {
    Optional<double[]> geocode(String address, String neighborhood, String city);
}
