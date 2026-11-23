package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Alerta de alagamento por regressão de um ponto (GET /api/alagamento/pontos, SSE {@code flood-forecast}).
 * Nível: ALERTA (lâmina provável q50 chega a 15 cm), ATENCAO (só a pessimista q95), SEM_ALERTA, SEM_DADO (com motivo;
 * nunca vira SEM_ALERTA). Experimental: nenhum modelo treinado com rótulo de alagamento; balde não calibrado.
 */
public record AlagamentoPontoDTO(
        long floodPointId,
        String slug,
        String name,
        String level,
        Integer worstHorizonHours,
        String reason,
        String reasonText,
        Double hNowCm,
        Double rainLastHourMm,
        List<Horizon> horizons,
        Explanation explanation,
        String rainStation,
        String rainStationName,
        Double rainStationDistanceKm,
        Double latestReadingAgeMinutes,
        Map<String, String> modelVersions,
        List<String> sources,
        List<String> missingInputs,
        String codeVersion,
        Instant issuedAt,
        Instant evaluatedAt,
        boolean experimental,
        boolean shadow,
        String rule,
        String attribution) {

    public static final String ATTRIBUTION = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";

    public record Horizon(int hours, String level, Double rainQ50Mm, Double rainQ95Mm, Double sheetQ50Cm, Double sheetQ95Cm,
                          Instant validUntil) {}

    /** Por que este nível, em linguagem física. */
    public record Explanation(String servedScenario, Integer servedHorizonHours, List<ShapItem> shapTop3,
                              Double counterfactualMm, String counterfactualText, Sensitivity sensitivity,
                              List<Step> decomposition, Double capacityM3H, Factors factors) {}

    /** Fatores por ponto do alerta único (ADR 0030): maré e limpeza, com a contribuição de cada um. */
    public record Factors(Refluxo refluxo, Limpeza limpeza) {}

    /**
     * Refluxo de maré. status: OK, DESLIGADO, NAO_SE_APLICA (sem saída cadastrada) ou SEM_DADO (maré ausente).
     * deltaSheetCm: lâmina com maré menos sem maré, por cenário (q50, q95) e horizonte ("1", "3", "6").
     */
    public record Refluxo(String mode, String status, boolean appliedToLevel, String reason, String text, Double sillNrM,
                          Double tideNowNrM, Double tideMaxHorizonNrM, Double reductionNowPct, List<TidePeriod> periods,
                          Double servedDeltaCm, Map<String, Map<String, Double>> deltaSheetCm, BiasScenario biasScenario) {}

    /** Trecho do horizonte em que a maré reduz a saída da galeria. */
    public record TidePeriod(Instant from, Instant to, Double meanReductionPct, Double maxReductionPct) {}

    /** Cenário de viés do MDT (MDT − RN = −0,369 m): só sensibilidade, nunca decide o nível. */
    public record BiasScenario(Double mdtBiasM, Double sillNrM, String level, Integer worstHorizonHours,
                               Map<String, Map<String, Double>> sheetCm) {}

    /** Limpeza (Central 156). status: OK, DESLIGADO ou SEM_DADO. probHcrit: P(lâmina ≥ 15 cm) da mistura livre/entupida. */
    public record Limpeza(String mode, String status, boolean appliedToLevel, String reason, String text, String regime,
                          Double daysSinceCleaning, Double pClogged, Double fClogged, String mixtureLevel,
                          Map<String, Map<String, Double>> sheetCloggedCm, Map<String, Map<String, Double>> probHcrit) {}

    /** Uma entrada do rain_v1 que pesou na chuva prevista: texto físico e contribuição em mm. */
    public record ShapItem(String text, Double contributionMm) {}

    public record Sensitivity(Double minCm, Double maxCm, boolean levelChanges, String heaviestParameter, String text) {}

    /** Um passo de 10 min do balde: medido (antes de agora) ou previsto (depois). */
    public record Step(Instant t, int minutesFromNow, String kind, Double rainMm, Double inflowM3H, Double outflowM3H,
                       Double volumeM3, Double sheetCm, Double tideNrM, Double outflowFactor) {}
}
