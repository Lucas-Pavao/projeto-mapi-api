package com.projeto.mapi.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Previsão oficial do INMET por município (prompt dos horizontes, bloco A). Informação oficial: aparece como "INMET",
 * nunca com as cores nem com os números do MAPi. Sem quantidade de chuva na origem. {@code firstSeenAt} e
 * {@code lastSeenAt} são quando o MAPi viu a versão, não a emissão (o INMET não publica a hora de emissão).
 * {@code reason}/{@code reasonText} só quando não há previsão para mostrar ou ela está velha.
 */
public record OfficialForecastDTO(
        String source,
        String sourceLabel,
        String attribution,
        String note,
        String ibge,
        String municipality,
        Instant lastCollectedAt,
        Long ageMinutes,
        boolean stale,
        String reason,
        String reasonText,
        List<Day> days
) {
    public static final String SOURCE = "INMET";
    public static final String SOURCE_LABEL = "previsão oficial do INMET";
    public static final String ATTRIBUTION = "INMET — Instituto Nacional de Meteorologia (https://portal.inmet.gov.br)";
    public static final String NOTE = "A previsão do INMET por município não traz quantidade de chuva. "
            + "\"Visto às\" é quando o MAPi viu a previsão; o INMET não publica a hora de emissão.";
    /** Idade máxima aceita (igual ao max_age_minutes da fonte em source_registry, V41). */
    public static final long MAX_AGE_MINUTES = 720;

    public record Day(LocalDate date, List<Period> periods) {}

    public record Period(String period, String summary, String iconCode, BigDecimal tempMax, BigDecimal tempMin,
                         BigDecimal humidityMax, BigDecimal humidityMin, String windDir, String windIntensity,
                         Instant firstSeenAt, Instant lastSeenAt) {}
}
