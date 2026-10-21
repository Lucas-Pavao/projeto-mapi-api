package com.projeto.mapi.dto;

/** O que o cadastro herda de um convite consumido: perfil e, se o ADMIN informou, o e-mail. */
public record InviteRedemptionDTO(String role, String email) {
}
