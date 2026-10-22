package com.projeto.mapi.service.ingestion.validation;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.List;

/**
 * Normalização e conversão de fuso, puras e sem depender do fuso da JVM (ADR 0006 seção 4).
 * {@link #normalize} produz {@code source_ts_norm}: ISO-8601 LOCAL da fonte, sem fuso aplicado
 * (a identidade do registro não muda se a agência trocar o formato do texto). {@link #toInstant}
 * é a ÚNICA conversão para UTC e só é chamada pela validação.
 */
public final class TimestampNormalizer {

    private static final List<DateTimeFormatter> DATE_TIME_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE_TIME,
            // ANA (Hidro-WebService): "2026-10-21 01:00:00.0", com fração de segundo
            new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm")
                    .optionalStart().appendPattern(":ss")
                    .optionalStart().appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true).optionalEnd()
                    .optionalEnd().toFormatter(),
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm[:ss]"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm[:ss]"));

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"));

    private TimestampNormalizer() {}

    /** Texto original -> ISO local ({@code 2026-10-10T14:00}, {@code 2026-10-10}); ilegível volta aparado. */
    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.trim();
        try {
            return OffsetDateTime.parse(t).toString();
        } catch (DateTimeException ignored) {
            // segue para os formatos locais
        }
        for (DateTimeFormatter f : DATE_TIME_FORMATS) {
            try {
                return LocalDateTime.parse(t, f).toString();
            } catch (DateTimeException ignored) {
                // tenta o próximo
            }
        }
        for (DateTimeFormatter f : DATE_FORMATS) {
            try {
                return LocalDate.parse(t, f).toString();
            } catch (DateTimeException ignored) {
                // tenta o próximo
            }
        }
        return t;
    }

    /** True se {@code norm} é somente data (sem hora). */
    public static boolean isDateOnly(String norm) {
        return norm != null && norm.length() == 10 && norm.indexOf('T') < 0;
    }

    /**
     * Converte {@code source_ts_norm} no instante UTC pelo fuso da fonte. O texto passa de novo por
     * {@link #normalize}: o bruto é append-only e pode guardar um {@code source_ts_norm} de um parser antigo.
     */
    public static Instant toInstant(String stored, String tz) {
        if (UNVERIFIED.equals(tz)) throw new TzUnverifiedException("Fuso da fonte não verificado");
        ZoneId zone = zone(tz);
        String norm = normalize(stored);
        try {
            if (norm.endsWith("Z") || norm.matches(".*[+-]\\d{2}:\\d{2}$")) {
                return OffsetDateTime.parse(norm).toInstant();
            }
            if (isDateOnly(norm)) return LocalDate.parse(norm).atStartOfDay(zone).toInstant();
            return LocalDateTime.parse(norm).atZone(zone).toInstant();
        } catch (DateTimeException e) {
            throw new TsUnparseableException("Texto de data/hora não interpretável: '" + norm + "'");
        }
    }

    /** Fim do dia civil local {@code D} (= início de D+1 local), em UTC. Usado por resoluções P1D. */
    public static Instant endOfLocalDay(String normDate, String tz) {
        if (UNVERIFIED.equals(tz)) throw new TzUnverifiedException("Fuso da fonte não verificado");
        try {
            return LocalDate.parse(normDate.substring(0, 10)).plusDays(1).atStartOfDay(zone(tz)).toInstant();
        } catch (DateTimeException | StringIndexOutOfBoundsException e) {
            throw new TsUnparseableException("Texto de data não interpretável: '" + normDate + "'");
        }
    }

    public static final String UNVERIFIED = "UNVERIFIED";

    private static ZoneId zone(String tz) {
        try {
            return "UTC".equals(tz) ? java.time.ZoneOffset.UTC : ZoneId.of(tz);
        } catch (DateTimeException e) {
            throw new TzUnverifiedException("Fuso desconhecido: '" + tz + "'");
        }
    }
}
