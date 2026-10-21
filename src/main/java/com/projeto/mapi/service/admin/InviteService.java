package com.projeto.mapi.service.admin;

import com.projeto.mapi.dto.InviteRequestDTO;
import com.projeto.mapi.dto.InviteResponseDTO;

/** Convites de uso único, validade de 72 h (ADR 0010). */
public interface InviteService {

    InviteResponseDTO create(InviteRequestDTO request, String actingUsername);

    /**
     * Consome o convite no cadastro e devolve o nome do perfil pré-definido.
     * {@code InvalidRequestException} se o token for inexistente, usado ou vencido.
     */
    String redeem(String token, String newUsername);
}
