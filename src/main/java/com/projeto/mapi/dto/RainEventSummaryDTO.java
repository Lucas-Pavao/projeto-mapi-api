package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Parâmetros agregados por tipo de evento (ADR 0017). Só eventos não truncados entram. A ordem dos campos é a ordem das
 * colunas do CSV.
 */
@Schema(description = "Resumo por ponto e tipo (FRACA, MEDIA, FORTE, TOTAL); eventos truncados ficam de fora")
public record RainEventSummaryDTO(
        String floodPointSlug,
        String type,
        @Schema(description = "Eventos não truncados, com ou sem lacuna") Integer count,
        @Schema(description = "Eventos não truncados e sem lacuna") Integer countWithoutGap,
        @Schema(description = "Eventos truncados excluídos do resumo") Integer truncatedExcluded,
        @Schema(description = "count ÷ anos nominais da janela") Double countPerYear,
        @Schema(description = "count ÷ anos cobertos (horas com valor ÷ 8766)") Double countPerYearCovered,
        Double meanDurationMin,
        Double medianDurationMin,
        Double meanIntensityMmH,
        @Schema(description = "Percentil 90 da intensidade média dos eventos (interpolação linear)") Double p90IntensityMmH,
        Double meanDryHoursBefore,
        Double meanTotalMm,
        @Schema(description = "Horas de evento desse tipo ÷ horas cobertas da janela") Double fractionOfTime,
        Integer eventsAboveCapacity,
        @Schema(description = "Média de hoursAboveCapacity entre os eventos que passaram da capacidade") Double meanHoursAboveCapacity,
        @Schema(description = "Horas com valor ÷ horas nominais da janela") Double coverageFraction) {
}
