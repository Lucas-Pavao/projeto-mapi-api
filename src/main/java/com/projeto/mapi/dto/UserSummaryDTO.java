package com.projeto.mapi.dto;

import java.time.Instant;

/** Contrato UserSummary. {@code email} e {@code name} são nulos nos usuários criados antes da V19. */
public record UserSummaryDTO(Long id, String name, String email, String role, boolean active, Instant lastLoginAt) {
}
