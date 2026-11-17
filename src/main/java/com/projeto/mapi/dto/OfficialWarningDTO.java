package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/** Aviso meteorológico oficial vigente do INMET (ADR 0024). */
public record OfficialWarningDTO(
        int warningId,
        int sequenceId,
        Integer externalId,
        String capCode,
        String source,
        Instant firstSeenAt,
        Instant lastSeenAt,
        Instant validFrom,
        Instant validTo,
        String severity,
        Integer severityId,
        String colorHex,
        String description,
        List<String> risks,
        List<String> instructions,
        List<String> geocodes,
        String municipalities,
        boolean altered,
        boolean closed
) {}
