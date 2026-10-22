package com.projeto.mapi.dto;

import java.time.Instant;

public record FloodReportDTO(
        Long id,
        Double latitude,
        Double longitude,
        Instant observedAt,
        String waterDepth,
        String description,
        String status,
        String nearestPointSlug,
        Integer nearestPointDistanceM,
        Instant createdAt,
        Instant reviewedAt,
        String reviewedBy,
        Long floodEventId,
        String discardReason) {
}
