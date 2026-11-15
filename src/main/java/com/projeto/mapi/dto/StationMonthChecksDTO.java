package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/** Grade estação x mês da checagem espacial (ADR 0022). */
@Schema(description = "Grade estação x mês da checagem espacial; célula ausente = mês não checado")
public record StationMonthChecksDTO(
        String ruleVersion,
        @Schema(description = "Início de cada mês da grade, UTC, em ordem") List<Instant> months,
        List<StationRow> stations) {

    public record StationRow(String stationId, String name, List<Cell> cells) {
    }

    public record Cell(Instant monthStartUtc, String status, List<String> reasons) {
    }
}
