package com.projeto.mapi.dto;

import java.time.Instant;

/**
 * Resposta pública de "Minha rua" (ADR 0013) pelo alerta por regressão: nível do ponto monitorado mais perto
 * (ALERTA, ATENCAO, SEM_ALERTA, SEM_DADO), sem probabilidade e sem coordenada do pedido. {@code inTest}: o alerta está
 * em modo sombra; o nível vai SEM_DADO e a manchete diz que está em teste. {@code meaning} é a manchete da tela.
 */
public record PublicRiskDTO(
        NearestPoint nearestPoint,
        Double rainLastHourMm,
        String rainStationId,
        boolean experimental,
        boolean inTest,
        String meaning) {

    public record NearestPoint(String slug, String name, int distanceM, String level, Integer worstHorizonHours,
                               Instant issuedAt, String reason) {
    }
}
