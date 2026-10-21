package com.projeto.mapi.dto;

import jakarta.validation.constraints.Pattern;

/** PATCH /api/admin/users/{id}: ao menos um campo. */
public record UserPatchRequestDTO(
        @Pattern(regexp = "USER|OPERADOR|ADMIN", message = "perfil deve ser USER, OPERADOR ou ADMIN") String role,
        Boolean active) {
}
