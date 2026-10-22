package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

public record AlertRuleDTO(
        Long id,
        String kind,
        String floodPointSlug,
        String stationId,
        Double threshold,
        String thresholdSource,
        String minRisk,
        boolean enabled,
        List<String> channels,
        String createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
