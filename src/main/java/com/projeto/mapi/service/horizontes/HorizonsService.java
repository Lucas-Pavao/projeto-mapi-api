package com.projeto.mapi.service.horizontes;

import com.projeto.mapi.dto.HorizonsDTO;

/** Leitura por horizonte de um ponto: INMET, "dia de risco" e próxima hora, sem combinar (prompt dos horizontes, D1). */
public interface HorizonsService {

    /** {@code admin}: vê o nível do alerta em modo sombra. Lança {@code ResourceNotFoundException} se o ponto não existe. */
    HorizonsDTO forPoint(String slug, boolean admin);
}
