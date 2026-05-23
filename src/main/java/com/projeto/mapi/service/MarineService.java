package com.projeto.mapi.service;

import com.fasterxml.jackson.databind.JsonNode;

public interface MarineService {
    JsonNode getMarineData(double latitude, double longitude);
    Double getCurrentWaveHeight(double latitude, double longitude);
    Double getCurrentWaveDirection(double latitude, double longitude);
    Double getCurrentWavePeriod(double latitude, double longitude);
}
