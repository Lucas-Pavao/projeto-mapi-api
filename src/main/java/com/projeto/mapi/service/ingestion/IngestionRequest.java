package com.projeto.mapi.service.ingestion;

import java.time.Instant;

/** Pedido de execução do pipeline para {@code [from, to)} de uma fonte. */
public record IngestionRequest(String source, Instant from, Instant to, IngestionMode mode, IngestionKind kind) {}
