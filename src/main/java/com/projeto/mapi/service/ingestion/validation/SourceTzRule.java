package com.projeto.mapi.service.ingestion.validation;

/** Fuso efetivo da fonte (source_registry) no momento da validação. */
public record SourceTzRule(String tz, String verificationState) {
    public boolean unverified() {
        return TimestampNormalizer.UNVERIFIED.equals(tz) || "UNVERIFIED".equals(verificationState);
    }
}
