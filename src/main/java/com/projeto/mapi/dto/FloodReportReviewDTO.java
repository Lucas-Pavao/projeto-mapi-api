package com.projeto.mapi.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/reports/{id}. CONFIRMED exige floodPointSlug e severity; DISCARDED exige reason
 * (a coerência é conferida no serviço).
 */
public record FloodReportReviewDTO(
        @NotNull(message = "status é obrigatório")
        @Pattern(regexp = "CONFIRMED|DISCARDED", message = "status deve ser CONFIRMED ou DISCARDED") String status,
        String floodPointSlug,
        @Pattern(regexp = "LOW|MEDIUM|HIGH|CRITICAL", message = "severity deve ser LOW, MEDIUM, HIGH ou CRITICAL") String severity,
        @Size(max = 500, message = "note aceita até 500 caracteres") String note,
        @Size(max = 500, message = "reason aceita até 500 caracteres") String reason) {
}
