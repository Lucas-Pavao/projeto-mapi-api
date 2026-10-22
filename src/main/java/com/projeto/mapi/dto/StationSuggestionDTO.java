package com.projeto.mapi.dto;

import java.time.Instant;

public record StationSuggestionDTO(String stationId, String name, String type, String source, int distanceM,
                                   Instant lastReadingAt) {
}
