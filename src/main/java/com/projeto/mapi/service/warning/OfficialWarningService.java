package com.projeto.mapi.service.warning;

import com.projeto.mapi.dto.OfficialWarningsResponseDTO;

/** Coleta e consulta de avisos meteorológicos oficiais vigentes (ADR 0024). */
public interface OfficialWarningService {

    /** Baixa os avisos do INMET, grava o bruto e persiste os que tocam os municípios configurados. */
    int syncInmetActive();

    /** Consulta os avisos vigentes agora; {@code municipio} aceita nome (ex.: "Recife") ou IBGE de 7 dígitos. */
    OfficialWarningsResponseDTO activeWarnings(String municipio);
}
