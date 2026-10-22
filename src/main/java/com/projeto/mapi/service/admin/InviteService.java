package com.projeto.mapi.service.admin;

import com.projeto.mapi.dto.InviteRequestDTO;
import com.projeto.mapi.dto.InviteResponseDTO;

/** Convites de uso único, validade de 72 h (ADR 0010). */
public interface InviteService {

    InviteResponseDTO create(InviteRequestDTO request, String actingUsername);

    /**
     * Consome o convite no cadastro e devolve o perfil pré-definido e o e-mail do convite (pode ser nulo).
     * {@code InvalidRequestException} se o token for inexistente, usado ou vencido.
     */
    com.projeto.mapi.dto.InviteRedemptionDTO redeem(String token, String newUsername);
}
