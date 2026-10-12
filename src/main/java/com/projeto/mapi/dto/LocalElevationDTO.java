package com.projeto.mapi.dto;

/**
 * Altitude do local consultado no mapa. {@code source}: {@code PE3D_MDT_5M} (cota do MDT do PE3D, média de 5 m; datum não
 * confirmado, cota relativa, ADR 0009) ou {@code OPEN_METEO_90M} (modelo de terreno de 90 m do Open-Meteo, aproximado,
 * usado só fora da área do MDT).
 */
public record LocalElevationDTO(Double meters, String source, Double resolutionM, String note) {}
