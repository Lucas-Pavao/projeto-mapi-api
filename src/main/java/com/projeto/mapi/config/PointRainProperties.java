package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Parâmetros de proveniência da chuva no ponto (ADR 0022 e ADR 0025 proposta). Prefixo {@code mapi.analise.point-rain}.
 *
 * <p>{@code allowReanalysisFallback} permanece {@code false} por padrão: treinar com reanálise e servir com
 * chuva medida foi o erro apontado no relatório de 2026-11-16. Quando explicitamente ligada em experimento, as linhas
 * saem marcadas com {@code SYNTHETIC_RAIN_FALLBACK} e fator 0,60, nunca entrando na métrica estrita.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.analise.point-rain")
public class PointRainProperties {

    /** Raio primário (km): estações até aqui saem como MEASURED_PRIMARY com confiança 1.0. */
    private double primaryRadiusKm = 3.0;

    /** Raio estendido máximo (km): entre primaryRadiusKm e maxRadiusKm sai MEASURED_EXTENDED (DISTANT_STATION). */
    private double maxRadiusKm = 5.0;

    /** Lacuna consecutiva mínima (horas) em janela chuvosa antes de considerar fallback de reanálise (quando ligado). */
    private int maxGapHoursBeforeFallback = 2;

    /** Desligado por padrão (ADR 0022 / relatório 16/11): nunca misturar reanálise no treino sem flag explícita. */
    private boolean allowReanalysisFallback = false;

    /** Fator de confiança aplicado a estações entre primaryRadiusKm e maxRadiusKm (MEASURED_EXTENDED). */
    private double distantStationConfidence = 0.85;

    /** Fator de confiança aplicado quando allowReanalysisFallback = true e ocorre REANALYSIS_FALLBACK. */
    private double reanalysisConfidence = 0.60;
}
