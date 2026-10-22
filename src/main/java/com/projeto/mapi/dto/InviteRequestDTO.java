package com.projeto.mapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record InviteRequestDTO(
        @NotNull(message = "o perfil é obrigatório")
        @Pattern(regexp = "USER|OPERADOR|ADMIN", message = "perfil deve ser USER, OPERADOR ou ADMIN") String role,
        @Email(message = "e-mail inválido") String email) {
}
