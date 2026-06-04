package com.projeto.mapi.service;

import java.util.Optional;

public interface GeocodingService {
    Optional<double[]> geocode(String address, String neighborhood, String city);
}
