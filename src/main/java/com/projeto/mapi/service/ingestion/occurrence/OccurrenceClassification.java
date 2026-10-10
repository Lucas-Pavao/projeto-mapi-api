package com.projeto.mapi.service.ingestion.occurrence;

import java.time.Instant;

/** Resultado da classificação de um registro CKAN. */
public record OccurrenceClassification(
        Status status,
        String matchedText,
        Instant occurredFrom,
        Instant occurredTo,
        String precision,
        String address,
        String neighborhood,
        String detail) {

    public enum Status { ACCEPTED, EXCLUDED_BY_FILTER, TS_UNPARSEABLE }
}
