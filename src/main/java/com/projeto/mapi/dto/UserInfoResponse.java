package com.projeto.mapi.dto;

/** GET /api/auth/me. {@code email} e {@code name} são nulos nos usuários criados antes da V19. */
public record UserInfoResponse(Long id, String username, String email, String name, String role) {
}
