package com.projeto.mapi.dto;

import java.time.Instant;

/**
 * Chuva MEDIDA acumulada no ponto (onda 16, Fase 7.3): hora do ponto = maior valor entre as estações válidas a até 5 km
 * (ADR 0022, regra chuva-ponto-v4); acumulado de N h com qualquer hora sem valor é nulo (lacuna nunca vira zero);
 * {@code nNh} diz quantas horas da janela tinham valor. Substitui, no card do ponto, a chuva estimada do Open-Meteo.
 */
public record PointMeasuredRainDTO(
        String slug,
        Instant hourEndUtc,
        Double rain1hMm,
        Double rain3hMm,
        Double rain6hMm,
        Double rain24hMm,
        Integer n3h,
        Integer n6h,
        Integer n24h,
        String source,
        String stationId,
        Double stationDistanceM,
        String ruleVersion,
        String rule,
        String attribution) {

    public static final String RULE = "maior valor da hora entre as estações válidas a até 5 km (ADR 0022); acumulado com "
            + "hora sem valor é nulo";
}
