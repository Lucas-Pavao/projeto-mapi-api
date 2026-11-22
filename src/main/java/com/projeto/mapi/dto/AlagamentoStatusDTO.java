package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.Map;

/**
 * Estado do alerta por regressão para a tela Dados (5.8): versões da MAPI AI pelo /health (commit real, item 0.4) e o
 * último ciclo (quando rodou, pontos por nível e SEM_DADO por motivo). {@code aiReachable=false}: a IA não respondeu.
 */
public record AlagamentoStatusDTO(boolean aiReachable, String rainModelVersion, String bucketVersion, String floodVersion,
                                  String codeVersion, String codeDirty, Instant lastCycleAt, Map<String, Integer> byLevel,
                                  Map<String, Integer> semDadoByReason, boolean shadow) {}
