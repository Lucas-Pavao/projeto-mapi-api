package com.projeto.mapi.service.sensor;

import com.projeto.mapi.dto.SensorReadingEventDTO;

/** Publicado quando uma leitura de sensor é gravada; o stream SSE o repassa (ADR 0012). */
public record SensorReadingSavedEvent(SensorReadingEventDTO reading) {
}
