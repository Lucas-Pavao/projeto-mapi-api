package com.projeto.mapi.service.weather;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Coleta do Open-Meteo por modelo (prompt dos horizontes, bloco B). Nada aqui entra no rain_v1 nem no alerta. */
public interface OpenMeteoModelService {

    /** Para cada modelo com rodada nova publicada, grava as horas até 48 h à frente dos pontos ativos. */
    int collectLive();

    /** Carga do histórico de rodadas (1, 2 e 3 dias de antecedência) desde {@code from}, retomável, em segundo plano. */
    void backfillAsync(LocalDate from);

    /** Linhas por modelo e produto (tela/admin). */
    List<Map<String, Object>> summary();
}
