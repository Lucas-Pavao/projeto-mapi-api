package com.projeto.mapi.service.publico;

import com.projeto.mapi.dto.PublicRiskDTO;

/** Risco perto de uma coordenada, para qualquer pessoa, sem login (ADR 0013). Só lê; nunca chama a IA. */
public interface PublicRiskService {

    /**
     * Arredonda lat/lon a 3 casas antes de qualquer uso. {@code UnprocessableEntityException} (422) fora da caixa da RMR.
     * Sem ponto ativo a até 1 km, {@code nearestPoint} é nulo.
     */
    PublicRiskDTO assess(double lat, double lon);
}
