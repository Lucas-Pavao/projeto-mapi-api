package com.projeto.mapi.dto;

import jakarta.validation.constraints.Pattern;

/** PATCH /api/admin/users/{id}: ao menos um campo. */
public record UserPatchRequestDTO(
        @Pattern(regexp = "USER|OPERADOR|ADMIN", message = "perfil deve ser USER, OPERADOR ou ADMIN") String role,
        Boolean active,
        @jakarta.validation.constraints.Email(message = "e-mail inválido")
        @jakarta.validation.constraints.Size(max = 255, message = "e-mail aceita até 255 caracteres") String email,
        @jakarta.validation.constraints.Size(max = 255, message = "nome aceita até 255 caracteres") String name) {
}
