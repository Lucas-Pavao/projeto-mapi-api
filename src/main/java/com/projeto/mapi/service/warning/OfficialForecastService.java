package com.projeto.mapi.service.warning;

import com.projeto.mapi.dto.OfficialForecastDTO;

/** Previsão oficial do INMET por município (prompt dos horizontes, bloco A). */
public interface OfficialForecastService {

    /** Coleta os municípios-alvo (um por vez), grava o bruto e as versões. Devolve quantas versões eram novas. */
    int syncAll();

    /** Previsão vigente de hoje em diante (data civil de Recife). Município por código IBGE ou nome da RMR. */
    OfficialForecastDTO current(String municipio);
}
