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
                              List<Step> decomposition, Double capacityM3H) {}

    /** Uma entrada do rain_v1 que pesou na chuva prevista: texto físico e contribuição em mm. */
    public record ShapItem(String text, Double contributionMm) {}

    public record Sensitivity(Double minCm, Double maxCm, boolean levelChanges, String heaviestParameter, String text) {}

    /** Um passo de 10 min do balde: medido (antes de agora) ou previsto (depois). */
    public record Step(Instant t, int minutesFromNow, String kind, Double rainMm, Double inflowM3H, Double outflowM3H,
                       Double volumeM3, Double sheetCm) {}
}
