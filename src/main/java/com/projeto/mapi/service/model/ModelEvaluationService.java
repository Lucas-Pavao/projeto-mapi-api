package com.projeto.mapi.service.model;

import com.projeto.mapi.dto.ModelEvaluationDTO;

/** Serve o resultado congelado da avaliação F1, conferindo o SHA-256 a cada leitura. */
public interface ModelEvaluationService {

    /** {@code ServiceUnavailableException} (503) se o arquivo estiver ausente, ilegível, não for JSON ou divergir do hash esperado. */
    ModelEvaluationDTO read();
}
