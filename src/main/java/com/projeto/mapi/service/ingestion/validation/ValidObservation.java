package com.projeto.mapi.service.ingestion.validation;

import java.time.Instant;

public record ValidObservation(String variable, String resolution, Instant tsUtc, Instant intervalStartUtc,
                               double value, String unit, String qualifier, String valueHash) {}
