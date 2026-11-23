package com.projeto.mapi.repository.analise;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Leitura da grade horária por estação e gravação das tabelas derivadas da chuva por ponto (ADR 0022, V24):
 * {@code station_month_check}, {@code point_rain_hourly} e os vínculos {@code MAX_VALID} de {@code point_station_link}.
 */
@Repository
@RequiredArgsConstructor
public class PointRainRepository {

    public record StationRow(String stationId, String name, double lat, double lon) {}

    public record HourRow(Instant hourEndUtc, Double value, int nReadings, Double coveredMin) {}

    public record CheckRow(String source, String stationId, Instant monthStart, String status, List<String> reasons,
                           int hoursInMonth, int hoursWithValue, double coverage, Double totalMm, int nNeighbors,
                           Double ratioMedian, Double commonMmMedian, Map<String, Object> metrics, String ruleVersion) {}

    public record PointHourRow(long pointId, Instant hourEndUtc, Double value, String source, String stationId,
                               Double stationDistanceM, int nCandidates, int nValid, int nValidWithValue,
                               Integer nBlocks, Double coveredMin, long linkageConfigId, String ruleVersion,
                               String rainTier, Double rainConfidence) {
        public PointHourRow(long pointId, Instant hourEndUtc, Double value, String source, String stationId,
                            Double stationDistanceM, int nCandidates, int nValid, int nValidWithValue,
                            Integer nBlocks, Double coveredMin, long linkageConfigId, String ruleVersion) {
            this(pointId, hourEndUtc, value, source, stationId, stationDistanceM, nCandidates, nValid, nValidWithValue,
                    nBlocks, coveredMin, linkageConfigId, ruleVersion, null, null);
        }
    }

    public record HourProvenance(Instant hourEndUtc, Double value, Double stationDistanceM, String rainTier,
                                 Double rainConfidence) {}

    public record LinkRow(long pointId, String source, String stationId, double distanceM, double radiusM,
                          long linkageConfigId, Instant validFrom, Instant validTo) {}

    public record StoredHour(Instant hourEndUtc, Double value, String stationId, Double stationDistanceM, int nValid,
                             int nValidWithValue, Integer nBlocks, Double coveredMin) {}

    public record MonthCheckRow(String stationId, String name, Instant monthStart, String status, List<String> reasons) {}

    public record LinkedStation(String stationId, String name, double distanceM) {}

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public List<StationRow> stations(String source) {
        return jdbc.query("SELECT station_id, name, lat, lon FROM station WHERE source = ? AND lat IS NOT NULL "
                        + "AND lon IS NOT NULL ORDER BY station_id",
                (rs, i) -> new StationRow(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4)), source);
    }

    /** Horas de chuva da estação com fim em (from, to]; uma consulta por estação (a view calcula o lag por série). */
    public List<HourRow> hourly(String source, String stationId, Instant from, Instant to) {
        return jdbc.query("""
                SELECT hour_end_utc, value, n_readings, CAST(blocks_covered_min AS double precision) FROM observation_hourly_v
                WHERE source = ? AND station_id = ? AND variable = 'precipitation' AND hour_end_utc > ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc
                """, (rs, i) -> new HourRow(rs.getObject(1, OffsetDateTime.class).toInstant(),
                rs.getObject(2, Double.class), rs.getInt(3), rs.getObject(4, Double.class)),
                source, stationId, utc(from), utc(to));
    }

    public Optional<Long> linkageId(String method, double radiusM) {
        return jdbc.query("SELECT id FROM linkage_config WHERE kind = 'SENSOR_POINT' AND method = ? AND radius_m = ? "
                + "ORDER BY id LIMIT 1", (rs, i) -> rs.getLong(1), method, radiusM).stream().findFirst();
    }

    public void replaceChecks(String source, String ruleVersion, Instant from, Instant to, List<CheckRow> rows) {
        jdbc.update("DELETE FROM station_month_check WHERE source = ? AND rule_version = ? AND month_start_utc >= ? "
                + "AND month_start_utc < ?", source, ruleVersion, utc(from), utc(to));
        jdbc.batchUpdate("""
                INSERT INTO station_month_check (source, station_id, month_start_utc, status, reasons, hours_in_month,
                    hours_with_value, coverage, total_mm, n_neighbors, neighbor_ratio_median, neighbor_common_mm_median,
                    metrics, rule_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?)
                """, rows, 1000, (ps, r) -> {
            ps.setString(1, r.source());
            ps.setString(2, r.stationId());
            ps.setObject(3, utc(r.monthStart()));
            ps.setString(4, r.status());
            ps.setArray(5, ps.getConnection().createArrayOf("text", r.reasons().toArray()));
            ps.setInt(6, r.hoursInMonth());
            ps.setInt(7, r.hoursWithValue());
            ps.setDouble(8, r.coverage());
            ps.setObject(9, r.totalMm());
            ps.setInt(10, r.nNeighbors());
            ps.setObject(11, r.ratioMedian());
            ps.setObject(12, r.commonMmMedian());
            try {
                ps.setString(13, mapper.writeValueAsString(r.metrics()));
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            ps.setString(14, r.ruleVersion());
        });
    }

    public void replacePointHours(String ruleVersion, Instant from, Instant to, List<PointHourRow> rows) {
        jdbc.update("DELETE FROM point_rain_hourly WHERE rule_version = ? AND hour_end_utc > ? AND hour_end_utc <= ?",
                ruleVersion, utc(from), utc(to));
        jdbc.batchUpdate("""
                INSERT INTO point_rain_hourly (flood_point_id, hour_end_utc, value, source, station_id,
                    station_distance_m, n_candidates, n_valid, n_valid_with_value, n_blocks, blocks_covered_min,
                    linkage_config_id, rule_version, rain_tier, rain_confidence)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, 5000, (ps, r) -> {
            ps.setLong(1, r.pointId());
            ps.setObject(2, utc(r.hourEndUtc()));
            ps.setObject(3, r.value());
            ps.setString(4, r.source());
            ps.setString(5, r.stationId());
            ps.setObject(6, r.stationDistanceM());
            ps.setInt(7, r.nCandidates());
            ps.setInt(8, r.nValid());
            ps.setInt(9, r.nValidWithValue());
            ps.setObject(10, r.nBlocks());
            ps.setObject(11, r.coveredMin());
            ps.setLong(12, r.linkageConfigId());
            ps.setString(13, r.ruleVersion());
            ps.setString(14, r.rainTier());
            ps.setObject(15, r.rainConfidence());
        });
    }

    /** Horas de chuva composta e distância/proveniência para o export estratificado. */
    public List<HourProvenance> pointHoursWithProvenance(long pointId, String ruleVersion, Instant from, Instant to) {
        return jdbc.query("""
                SELECT hour_end_utc, value, station_distance_m, rain_tier, rain_confidence
                FROM point_rain_hourly
                WHERE flood_point_id = ? AND rule_version = ? AND hour_end_utc >= ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc
                """, (rs, i) -> new HourProvenance(
                rs.getObject(1, OffsetDateTime.class).toInstant(),
                rs.getObject(2, Double.class),
                rs.getObject(3, Double.class),
                rs.getString(4),
                rs.getObject(5, Double.class)),
                pointId, ruleVersion, utc(from), utc(to));
    }

    public void replaceLinks(long linkageConfigId, List<LinkRow> rows) {
        jdbc.update("DELETE FROM point_station_link WHERE linkage_config_id = ? AND method = 'MAX_VALID'",
                linkageConfigId);
        jdbc.batchUpdate("""
                INSERT INTO point_station_link (flood_point_id, source, station_id, variable_scope, distance_m, radius_m,
                    linkage_config_id, method, valid_from, valid_to)
                VALUES (?, ?, ?, 'precipitation', ?, ?, ?, 'MAX_VALID', ?, ?)
                """, rows, 500, (ps, r) -> {
            ps.setLong(1, r.pointId());
            ps.setString(2, r.source());
            ps.setString(3, r.stationId());
            ps.setDouble(4, r.distanceM());
            ps.setDouble(5, r.radiusM());
            ps.setLong(6, r.linkageConfigId());
            ps.setTimestamp(7, Timestamp.from(r.validFrom()));
            ps.setTimestamp(8, r.validTo() == null ? null : Timestamp.from(r.validTo()));
        });
    }

    /** Série composta do ponto com fim em [from, to] (leitura para a tabela de eventos). */
    public List<StoredHour> pointHours(long pointId, String ruleVersion, Instant from, Instant to) {
        return jdbc.query("""
                SELECT hour_end_utc, value, station_id, station_distance_m, n_valid, n_valid_with_value, n_blocks,
                       blocks_covered_min
                FROM point_rain_hourly
                WHERE flood_point_id = ? AND rule_version = ? AND hour_end_utc >= ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc
                """, (rs, i) -> new StoredHour(rs.getObject(1, OffsetDateTime.class).toInstant(),
                rs.getObject(2, Double.class), rs.getString(3), rs.getObject(4, Double.class), rs.getInt(5),
                rs.getInt(6), rs.getObject(7, Integer.class), rs.getObject(8, Double.class)),
                pointId, ruleVersion, utc(from), utc(to));
    }

    /** Checagens mensais da fonte na regra pedida, com início de mês em [from, to). Status no vocabulário do banco. */
    public List<MonthCheckRow> monthChecks(String source, String ruleVersion, Instant from, Instant to) {
        return jdbc.query("""
                SELECT c.station_id, s.name, c.month_start_utc, c.status, c.reasons
                FROM station_month_check c
                LEFT JOIN station s ON s.source = c.source AND s.station_id = c.station_id
                WHERE c.source = ? AND c.rule_version = ? AND c.month_start_utc >= ? AND c.month_start_utc < ?
                ORDER BY c.station_id, c.month_start_utc
                """, (rs, i) -> {
            java.sql.Array a = rs.getArray(5);
            List<String> reasons = a == null ? List.of() : java.util.Arrays.asList((String[]) a.getArray());
            return new MonthCheckRow(rs.getString(1), rs.getString(2), rs.getObject(3, OffsetDateTime.class).toInstant(),
                    rs.getString(4), reasons);
        }, source, ruleVersion, utc(from), utc(to));
    }

    /** Estações vinculadas ao ponto (MAX_VALID, chuva) cuja janela de vínculo cobre a hora que termina em {@code at}. */
    public List<LinkedStation> linkedStations(long pointId, String source, Instant at) {
        return jdbc.query("""
                SELECT l.station_id, s.name, l.distance_m FROM point_station_link l
                LEFT JOIN station s ON s.source = l.source AND s.station_id = l.station_id
                WHERE l.flood_point_id = ? AND l.source = ? AND l.method = 'MAX_VALID' AND l.variable_scope = 'precipitation'
                  AND l.valid_from < ? AND (l.valid_to IS NULL OR l.valid_to >= ?)
                ORDER BY l.distance_m, l.station_id
                """, (rs, i) -> new LinkedStation(rs.getString(1), rs.getString(2), rs.getDouble(3)),
                pointId, source, utc(at), utc(at));
    }

    /** Última hora da série do ponto na regra (point_rain_hourly não é hypertable; a chave é ponto + regra + hora). */
    public Optional<Instant> latestPointHour(long pointId, String ruleVersion) {
        return jdbc.query("SELECT max(hour_end_utc) FROM point_rain_hourly WHERE flood_point_id = ? AND rule_version = ?",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class), pointId, ruleVersion).stream()
                .filter(java.util.Objects::nonNull).map(OffsetDateTime::toInstant).findFirst();
    }

    /** Linha mais nova de point_rain_accum_v do ponto na regra, até {@code to} (onda 16, Fase 7.3). */
    public java.util.Optional<com.projeto.mapi.dto.PointMeasuredRainDTO> latestAccum(String slug, String ruleVersion, Instant to) {
        var rows = jdbc.query("""
                SELECT slug, hour_end_utc, rain_1h_mm, rain_3h_mm, rain_6h_mm, rain_24h_mm, rain_3h_n, rain_6h_n, rain_24h_n,
                       source, station_id, station_distance_m
                FROM point_rain_accum_v
                WHERE slug = ? AND rule_version = ? AND hour_end_utc <= ?
                ORDER BY hour_end_utc DESC LIMIT 1""",
                (rs, i) -> new com.projeto.mapi.dto.PointMeasuredRainDTO(rs.getString(1),
                        rs.getObject(2, java.time.OffsetDateTime.class).toInstant(), rs.getObject(3, Double.class),
                        rs.getObject(4, Double.class), rs.getObject(5, Double.class), rs.getObject(6, Double.class),
                        toInt(rs.getObject(7, Long.class)), toInt(rs.getObject(8, Long.class)), toInt(rs.getObject(9, Long.class)),
                        rs.getString(10), rs.getString(11), rs.getObject(12, Double.class), ruleVersion,
                        com.projeto.mapi.dto.PointMeasuredRainDTO.RULE, "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC"),
                slug, ruleVersion, java.sql.Timestamp.from(to));
        return rows.stream().findFirst();
    }

    private static Integer toInt(Long v) {
        return v == null ? null : v.intValue();
    }
}
