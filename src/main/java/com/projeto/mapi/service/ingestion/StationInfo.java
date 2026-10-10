package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

/** Metadado da estação da fonte (tabela station). */
public record StationInfo(String source, String stationId, String name, Double lat, Double lon,
                          String municipality, JsonNode attrs) {}
