package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Estações candidatas à chuva de um ponto numa hora e o papel de cada uma (ADR 0022). */
@Schema(description = "Candidatas da chuva do ponto (vínculo até 5 km) na hora pedida")
public record PointRainCandidatesDTO(
        String slug,
        @Schema(description = "Fim da hora, UTC") Instant hourEndUtc,
        double radiusM,
        String ruleVersion,
        @Schema(description = "Valor da hora do ponto (mm); nulo se não há série na hora") Double valueMm,
        @Schema(description = "Estação que forneceu o valor") String usedStationId,
        List<Candidate> candidates,
        @Schema(description = "Citação obrigatória dos dados do Cemaden") String attribution) {

    @Schema(description = "role: USED | LOWER | EXCLUDED | NO_VALUE")
    public record Candidate(
            String stationId,
            String name,
            double distanceM,
            @Schema(description = "Soma horária da estação (mm); nulo se sem leitura") Double valueMm,
            @Schema(description = "Status do mês da hora: OK | UNCHECKED | FAIL | OFFLINE; nulo se mês não checado") String monthStatus,
            List<String> reasons,
            String role) {
    }
}
