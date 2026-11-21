package com.projeto.mapi.service.alagamento;

import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.model.FloodPoint;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Alerta de alagamento por regressão por ponto (V38), avaliado dentro do ciclo do alerta por ponto (ADR 0026). */
public interface FloodForecastService {

    /** Entrada que o ciclo já tem: série medida, terreno e a estação da leitura mais nova. */
    record Input(List<Double> rain10min, FloodForecastClient.Terrain terrain, String terrainStatus, Double rainLastHourMm,
                 String rainStation, Double rainStationKm, double trYears, double teMinutes) {}

    /** Avalia, grava e devolve o estado do ponto. Nunca lança por falha da IA: vira SEM_DADO com motivo. */
    AlagamentoPontoDTO evaluate(UUID cycleId, Instant now, FloodPoint p, Input in);

    /** Último estado de cada ponto ativo. */
    List<AlagamentoPontoDTO> current();

    /** Histórico por ponto nas últimas {@code hours} horas (relatório e linha do tempo). */
    com.projeto.mapi.dto.AlagamentoHistoricoDTO history(int hours);

    /** Versões da IA e o último ciclo (tela Dados). */
    com.projeto.mapi.dto.AlagamentoStatusDTO status();

    boolean enabled();

    boolean shadow();
}
