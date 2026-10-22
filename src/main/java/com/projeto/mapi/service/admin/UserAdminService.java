package com.projeto.mapi.service.admin;

import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.dto.UserPatchRequestDTO;
import com.projeto.mapi.dto.UserSummaryDTO;

/** Gestão de usuários pelo ADMIN (ADR 0010). */
public interface UserAdminService {

    PageResponseDTO<UserSummaryDTO> list(int page, int size);

    /**
     * Troca perfil e/ou ativa/desativa. 409 se o alvo é o próprio usuário ou se o sistema ficaria sem ADMIN ativo.
     * Desativar revoga o refresh token.
     */
    UserSummaryDTO update(Long id, UserPatchRequestDTO patch, String actingUsername);
}
