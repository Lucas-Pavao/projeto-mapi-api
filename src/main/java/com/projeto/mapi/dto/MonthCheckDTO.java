package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Resultado da checagem espacial de uma estação num mês (ADR 0022). */
@Schema(description = "Checagem espacial mensal da estação PED")
public record MonthCheckDTO(
        @Schema(description = "Primeiro instante do mês civil, UTC") Instant monthStartUtc,
        @Schema(description = "OK | UNCHECKED | FAIL | OFFLINE (no banco, OK é PASS)") String status,
        @Schema(description = "PARTIAL_COVERAGE, STUCK_ZERO, UNDERCATCH, OVERCATCH, FEW_WET_NEIGHBORS") List<String> reasons,
        @Schema(description = "Versão da regra que gerou a checagem, ex.: chuva-ponto-v4") String ruleVersion) {
}
