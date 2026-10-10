package com.projeto.mapi.service.ingestion;

/** Parte da identidade do registro de origem dentro de uma fonte. */
public record OriginKey(String stationId, String sourceTsNorm) {}
