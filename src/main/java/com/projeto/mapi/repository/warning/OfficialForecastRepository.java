package com.projeto.mapi.repository.warning;

import com.projeto.mapi.service.warning.InmetForecastParser.ParsedForecast;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Previsão oficial do INMET por município (V41). Cada versão vista é uma linha; revisitar só avança last_seen_at. */
@Repository
@RequiredArgsConstructor
public class OfficialForecastRepository {

    public record Row(String ibge, String municipality, LocalDate forecastDate, String period, String summary,
                      String weather, String iconCode, BigDecimal tempMax, BigDecimal tempMin, BigDecimal humidityMax,
                      BigDecimal humidityMin, String windDir, String windIntensity, String rainFieldsJson,
                      Instant firstSeenAt, Instant lastSeenAt) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Grava as versões novas e avança last_seen_at das já vistas. Devolve quantas versões eram novas. */
    public int upsertAll(String source, UUID runId, Instant seenAt, String rawHash, List<ParsedForecast> entries) {
        int inserted = 0;
        OffsetDateTime seen = utc(seenAt);
        for (ParsedForecast f : entries) {
            Integer isNew = jdbc.queryForObject("""
                    INSERT INTO official_forecast (source, ibge, municipality, uf, forecast_date, period, summary, weather,
                        icon_code, temp_max, temp_min, humidity_max, humidity_min, wind_dir, wind_intensity, temp_max_trend,
                        temp_min_trend, issue_hour_text, rain_fields, entry_hash, raw_hash, first_seen_at, last_seen_at, run_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?)
                    ON CONFLICT (source, ibge, forecast_date, period, entry_hash) DO UPDATE SET last_seen_at = EXCLUDED.last_seen_at
                    RETURNING (xmax = 0)::int
                    """, Integer.class, source, f.ibge(), f.municipality(), f.uf(), Date.valueOf(f.forecastDate()), f.period(),
                    f.summary(), f.weather(), f.iconCode(), f.tempMax(), f.tempMin(), f.humidityMax(), f.humidityMin(),
                    f.windDir(), f.windIntensity(), f.tempMaxTrend(), f.tempMinTrend(), f.issueHourText(), f.rainFieldsJson(),
                    f.entryHash(), rawHash, seen, seen, runId);
            if (isNew != null && isNew == 1) inserted++;
        }
        return inserted;
    }

    /**
     * Previsão vigente: por data e período a partir de {@code fromDate}, a versão vista mais recentemente, e só entre as
     * versões da última coleta que trouxe aquela data (uma versão antiga nunca reaparece como vigente).
     */
    public List<Row> current(String source, String ibge, LocalDate fromDate) {
        return jdbc.query("""
                SELECT DISTINCT ON (forecast_date, period) ibge, municipality, forecast_date, period, summary, weather,
                       icon_code, temp_max, temp_min, humidity_max, humidity_min, wind_dir, wind_intensity, rain_fields::text,
                       first_seen_at, last_seen_at
                FROM official_forecast
                WHERE source = ? AND ibge = ? AND forecast_date >= ?
                ORDER BY forecast_date, period, last_seen_at DESC, first_seen_at DESC
                """, (rs, i) -> new Row(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class), rs.getString(4),
                rs.getString(5), rs.getString(6), rs.getString(7), rs.getBigDecimal(8), rs.getBigDecimal(9),
                rs.getBigDecimal(10), rs.getBigDecimal(11), rs.getString(12), rs.getString(13), rs.getString(14),
                rs.getObject(15, OffsetDateTime.class).toInstant(), rs.getObject(16, OffsetDateTime.class).toInstant()),
                source, ibge, Date.valueOf(fromDate));
    }

    /** Instante da última coleta bem-sucedida da fonte (para a idade na tela). */
    public Instant lastSuccessfulRun(String source) {
        OffsetDateTime t = jdbc.queryForObject("""
                SELECT max(finished_at) FROM ingestion_run WHERE source = ? AND status IN ('OK', 'PARTIAL')
                """, OffsetDateTime.class, source);
        return t == null ? null : t.toInstant();
    }
}
