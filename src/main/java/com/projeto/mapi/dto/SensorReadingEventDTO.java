package com.projeto.mapi.dto;

import java.time.Instant;

/** Evento SSE {@code sensor} (ADR 0012). */
public record SensorReadingEventDTO(String sensorId, String type, Double value, String unit, Instant observedAt) {
}
