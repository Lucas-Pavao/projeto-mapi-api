package com.projeto.mapi.dto;

import java.time.Instant;

public record AlertDTO(
        Long id,
        Long ruleId,
        String kind,
        String severity,
        String floodPointSlug,
        String stationId,
        String title,
        String detail,
        Double observedValue,
        String riskLevel,
        String degradationReason,
        String modelValidationStatus,
        Instant triggeredAt,
        Instant acknowledgedAt,
        String acknowledgedBy) {
}
