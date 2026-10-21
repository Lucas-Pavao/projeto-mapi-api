package com.projeto.mapi.support;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Linhas mínimas da série consolidada (ADR 0006) para testes de chuva horária. */
public final class ObservationFixtures {

    private ObservationFixtures() {
    }

    public static UUID run(JdbcTemplate jdbc, String source) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ingestion_run (id, source, kind, code_version, parser_version, rule_version, started_at, status) "
                + "VALUES (?, ?, 'LIVE', 't', 't', 't', now(), 'OK')", id, source);
        return id;
    }

    public static void station(JdbcTemplate jdbc, String source, String stationId, double lat, double lon) {
        jdbc.update("INSERT INTO station (source, station_id, name, lat, lon) VALUES (?,?,?,?,?) "
                + "ON CONFLICT (source, station_id) DO UPDATE SET lat = EXCLUDED.lat, lon = EXCLUDED.lon",
                source, stationId, stationId, lat, lon);
    }

    /** Uma leitura de chuva (mm) cujo intervalo termina em {@code tsEnd} (convenção END). */
    public static void precipitation(JdbcTemplate jdbc, UUID run, String source, String stationId, String resolution,
                                     Instant tsEnd, double mm, String status) {
        Duration len = switch (resolution) {
            case "PT5M" -> Duration.ofMinutes(5);
            case "PT10M" -> Duration.ofMinutes(10);
            case "PT15M" -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
        jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc, "
                        + "status, run_id, raw_hash, rule_version, value_hash, origin_ts_norm) "
                        + "VALUES (?, ?, 'precipitation', ?, ?, ?, 'mm', ?, ?, ?, repeat('a',64), 't', repeat('b',64), ?)",
                source, stationId, resolution, Timestamp.from(tsEnd), mm, Timestamp.from(tsEnd.minus(len)), status, run,
                tsEnd.toString());
    }

    public static void precipitation(JdbcTemplate jdbc, UUID run, String source, String stationId, Instant hourEnd, double mm) {
        precipitation(jdbc, run, source, stationId, "PT1H", hourEnd, mm, "ACTIVE");
    }

    public static void clear(JdbcTemplate jdbc, String source) {
        jdbc.update("DELETE FROM observation WHERE source = ?", source);
        jdbc.update("DELETE FROM station WHERE source = ?", source);
    }
}
