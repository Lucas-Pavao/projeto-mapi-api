package com.projeto.mapi.dto;

import com.projeto.mapi.service.ingestion.IngestionKind;
import com.projeto.mapi.service.ingestion.IngestionMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/** Corpo de POST /api/admin/ingestion/runs. Intervalo [from, to) em ISO-8601 com Z. */
public record IngestionRunRequestDTO(
        @NotBlank String source,
        @NotNull Instant from,
        @NotNull Instant to,
        @NotNull IngestionMode mode,
        IngestionKind kind) {}
