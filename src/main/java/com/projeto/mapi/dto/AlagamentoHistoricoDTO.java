package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/**
 * Histórico do alerta por regressão na janela pedida (5.10: relatório do evento e linha do tempo). Um item por ciclo:
 * nível, prazo do pior nível e lâmina de agora. Nunca probabilidade.
 */
public record AlagamentoHistoricoDTO(Instant from, Instant to, List<Series> points) {

    public record Series(String slug, String name, List<Item> items) {}

    public record Item(Instant at, String level, Integer worstHorizonHours, Double hNowCm, String reason) {}
}
