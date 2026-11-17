package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Estado atual do alerta de um ponto (ADR 0026). Dois campos com fontes e falhas diferentes. */
public record AlertaPontoDTO(
        String slug,
        String name,
        @Schema(description = "NENHUM, ATENCAO, ALERTA ou SEM_DADO") String rainAlert,
        @Schema(description = "motivo do SEM_DADO de rainAlert") String rainReason,
        String rainReasonText,
        @Schema(description = "NAO, PROVAVEL ou SEM_DADO") String pointFlood,
        String floodReason,
        String floodReasonText,
        Instant rainSince,
        String trigger,
        @Schema(description = "probabilidade de evento FORTE (25,15 mm/h) em 1 h, rain_v1") Double probForte1h,
        @Schema(description = "L_liga") Double probOn,
        @Schema(description = "L_desliga") Double probOff,
        @Schema(description = "chuva medida no ponto na duração Te (10 min), mm") Double rainTeMm,
        @Schema(description = "capacidade da galeria na duração Te: i(TR, Te) x Te / 60, mm") Double capacityTeMm,
        Double rain30minMm,
        @Schema(description = "lâmina estimada agora pelo balde a 10 min, cm") Double hNowCm,
        @Schema(description = "maior lâmina projetada nos próximos 60 min com o quantil de 50%, cm") Double hProjQ50MaxCm,
        @Schema(description = "idem com o quantil de 80% (faixa)") Double hProjQ80MaxCm,
        @Schema(description = "minutos até a lâmina chegar a 15 cm (0 = agora)") Integer floodInMinutes,
        @Schema(description = "lâmina por passo de 10 min (mm), q50 e q80") Map<String, Object> projection,
        Double q50_1hMm,
        Double q80_1hMm,
        String rainModelVersion,
        String bucketVersion,
        Double trYears,
        Double teMinutes,
        List<String> sources,
        Instant latestReadingUtc,
        Double latestReadingAgeMinutes,
        String eventId,
        @Schema(description = "modo sombra: gravado e conferido, não oficial") boolean shadow,
        Instant updatedAt,
        @Schema(description = "etiqueta obrigatória do nível do ponto") String physicalModelLabel,
        String attribution) {

    public static final String PHYSICAL_MODEL_LABEL = "Estimativa do modelo físico, ainda não validada contra alagamento observado";
    public static final String ATTRIBUTION = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";
}
