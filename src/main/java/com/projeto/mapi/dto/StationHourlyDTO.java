package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Série horária de chuva de uma estação canônica, com a fonte usada e o pico isolado marcado. */
@Schema(description = "Chuva horária (mm) da estação canônica, uma linha por hora da janela, inclusive sem dado")
public record StationHourlyDTO(
        String canonicalId,
        @Schema(description = "Início da janela (exclusivo), UTC") Instant from,
        @Schema(description = "Fim da janela (inclusivo), UTC") Instant to,
        @Schema(description = "Tolerância de divergência PED x ao vivo, em mm") double toleranceMm,
        List<Hour> hours,
        @Schema(description = "Citação obrigatória dos dados do Cemaden") String attribution) {

    @Schema(description = "Uma hora da série; valueMm nulo = sem dado ou pico isolado removido, nunca zero")
    public record Hour(
            @Schema(description = "Fim da hora, UTC") Instant hourEndUtc,
            @Schema(description = "Valor servido (mm); nulo se a hora não tem dado ou é pico isolado") Double valueMm,
            @Schema(description = "CEMADEN_PED se houver PED na hora; senão CEMADEN; senão nulo") String usedSource,
            @Schema(description = "Soma horária da PED (mm), nulo se ausente") Double pedMm,
            @Schema(description = "Soma horária da coleta ao vivo (mm), nulo se ausente") Double liveMm,
            @Schema(description = "|PED - ao vivo| acima da tolerância") boolean divergent,
            @Schema(description = "Hora com >= 10 mm e >= 2 vizinhas a até 6 km abaixo de 1 mm (RainSpatialCheck)") boolean isolatedPeakRemoved) {
    }
}
