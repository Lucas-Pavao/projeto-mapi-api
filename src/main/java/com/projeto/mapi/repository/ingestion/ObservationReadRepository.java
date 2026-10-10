package com.projeto.mapi.repository.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Leitura da grade horária determinística (observation_hourly_v) para o export. */
@Repository
@RequiredArgsConstructor
public class ObservationReadRepository {

    /** Uma célula da grade horária; value nulo = hora incompleta (nunca constante). */
    public record HourlyCell(String variable, Instant hourEndUtc, Double value, boolean flagged,
                             List<UUID> runIds, int maxRevision) {}

    private final JdbcTemplate jdbc;

    /** Horas com fim em [fromUtc, toUtc] de uma estação de uma fonte. */
    public List<HourlyCell> hourly(String source, String stationId, Instant fromUtc, Instant toUtc) {
        return jdbc.query("""
                SELECT variable, hour_end_utc, value, has_flag, run_ids, max_revision
                FROM observation_hourly_v
                WHERE source = ? AND station_id = ? AND hour_end_utc >= ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc, variable
                """, (rs, i) -> new HourlyCell(rs.getString("variable"),
                rs.getObject("hour_end_utc", OffsetDateTime.class).toInstant(), rs.getObject("value", Double.class),
                rs.getBoolean("has_flag"), uuids(rs.getArray("run_ids")), rs.getInt("max_revision")),
                source, stationId, OffsetDateTime.ofInstant(fromUtc, java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(toUtc, java.time.ZoneOffset.UTC));
    }

    private static List<UUID> uuids(java.sql.Array array) throws java.sql.SQLException {
        if (array == null) return List.of();
        Object[] raw = (Object[]) array.getArray();
        return java.util.Arrays.stream(raw).map(o -> UUID.fromString(o.toString())).toList();
    }
}
