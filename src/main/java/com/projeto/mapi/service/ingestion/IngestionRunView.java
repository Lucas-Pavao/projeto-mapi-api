package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

/** Leitura de um ingestion_run (resposta do endpoint admin e retorno do pipeline). */
public record IngestionRunView(
        UUID id, String source, String kind, String mode, String status,
        Instant intervalStart, Instant intervalEnd, Instant startedAt, Instant finishedAt,
        String codeVersion, String parserVersion, String ruleVersion, UUID parentRunId, String errorSummary,
        int fetched, int batchDeduped, int rawInserted, int rawUnchanged, int valid, int quarantined, int gaps,
        int obsInserted, int obsUpdated, int obsUnchanged, int obsRetracted, int absentInRefetch,
        JsonNode params) {}
