package com.projeto.mapi.dto;

import java.time.Instant;

/** Contrato UserSummary. {@code email} = username (o cadastro atual não tem e-mail separado). */
public record UserSummaryDTO(Long id, String name, String email, String role, boolean active, Instant lastLoginAt) {
}
