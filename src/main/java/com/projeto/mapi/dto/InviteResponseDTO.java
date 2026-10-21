package com.projeto.mapi.dto;

import java.time.Instant;

public record InviteResponseDTO(String token, String role, Instant expiresAt) {
}
