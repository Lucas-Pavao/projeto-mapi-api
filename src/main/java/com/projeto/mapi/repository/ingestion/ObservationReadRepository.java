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

    /** Extremo vigente da maré prevista; {@code qualifier} = PREAMAR ou BAIXA_MAR (pode ser nulo se ambíguo). */
    public record TideExtremeRow(Instant tsUtc, double level, String qualifier, String source) {}

    /** Estação mais próxima (haversine, m) de uma fonte que tem a variável na grade horária PT15M. */
    public record NearestStation(String stationId, double distanceM) {}

    private final JdbcTemplate jdbc;

    /**
     * Estação da fonte mais próxima da coordenada entre as que têm {@code variable} em {@code resolution}; empate
     * resolvido pelo código. Vazio se nenhuma tem a variável (F2-DADOS-11: chuva ANA no export).
     */
    public java.util.Optional<NearestStation> nearestStation(String source, String variable, String resolution,
                                                              double lat, double lon) {
        return jdbc.query("""
                SELECT s.station_id,
                       2 * 6371000 * asin(sqrt(power(sin(radians(s.lat - ?) / 2), 2)
                         + cos(radians(?)) * cos(radians(s.lat)) * power(sin(radians(s.lon - ?) / 2), 2))) AS d
                FROM station s
                WHERE s.source = ? AND s.lat IS NOT NULL AND s.lon IS NOT NULL
                  AND EXISTS (SELECT 1 FROM observation_current o WHERE o.source = s.source
                              AND o.station_id = s.station_id AND o.variable = ? AND o.resolution = ?)
                ORDER BY d, s.station_id LIMIT 1
                """, (rs, i) -> new NearestStation(rs.getString("station_id"), rs.getDouble("d")),
                lat, lat, lon, source, variable, resolution).stream().findFirst();
    }

    /**
     * Extremos vigentes (ACTIVE) da maré prevista no Porto do Recife em {@code [fromUtc, toUtc)}. Se as duas fontes
     * (API e tábua DHN) cobrem o mesmo instante, vale a da API.
     */
    public List<TideExtremeRow> tideExtremes(Instant fromUtc, Instant toUtc) {
        return jdbc.query("""
                SELECT DISTINCT ON (ts_utc) ts_utc, value, qualifier, source
                FROM observation_current
                WHERE station_id = 'pe02' AND variable = 'tide_height' AND resolution = 'EVENT'
                  AND source IN ('TABUAMARE_PE02', 'DHN_TABUA_PE02')
                  AND ts_utc >= ? AND ts_utc < ? AND value IS NOT NULL
                ORDER BY ts_utc, CASE source WHEN 'TABUAMARE_PE02' THEN 0 ELSE 1 END
                """, (rs, i) -> new TideExtremeRow(rs.getObject("ts_utc", OffsetDateTime.class).toInstant(),
                rs.getDouble("value"), rs.getString("qualifier"), rs.getString("source")),
                OffsetDateTime.ofInstant(fromUtc, java.time.ZoneOffset.UTC),
                OffsetDateTime.ofInstant(toUtc, java.time.ZoneOffset.UTC));
    }

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
