package com.projeto.mapi.repository.station;

import com.projeto.mapi.service.station.StationMatcher.Station;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Tabelas da estação canônica (V25, ADR 0023): {@code station_canonical}, {@code station_alias}, {@code station_hour_divergence}. */
@Repository
@RequiredArgsConstructor
public class StationCanonicalRepository {

    public record CanonicalRow(String canonicalId, String name, double lat, double lon, String municipality,
                               String ibgeCode, String primarySource, int nAliases, String ruleVersion) {}

    public record AliasRow(String source, String stationId, String canonicalId, String sourceName, double distanceM,
                           Double nameSimilarity, String ruleVersion) {}

    public record DivergenceRow(String canonicalId, Instant hourEndUtc, String keptSource, String keptStationId,
                                double keptValue, String otherSource, String otherStationId, double otherValue,
                                double diffMm, double toleranceMm, String ruleVersion) {}

    public record CanonicalView(String canonicalId, String name, double lat, double lon, String municipality,
                                String ibgeCode, String primarySource, List<String> aliases) {}

    public record HourValue(Instant hourEndUtc, double value) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    public List<Station> stations(String source) {
        return jdbc.query("SELECT source, station_id, name, lat, lon, municipality FROM station "
                        + "WHERE source = ? AND lat IS NOT NULL AND lon IS NOT NULL ORDER BY station_id",
                (rs, i) -> new Station(rs.getString(1), rs.getString(2), rs.getString(3), rs.getDouble(4),
                        rs.getDouble(5), rs.getString(6)), source);
    }

    /** Derivado: apaga tudo e grava de novo (a cascata leva apelidos e divergências). */
    public void replaceAll(List<CanonicalRow> canonicals, List<AliasRow> aliases) {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.batchUpdate("""
                INSERT INTO station_canonical (canonical_id, name, lat, lon, municipality, ibge_code, primary_source,
                                               n_aliases, rule_version) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, canonicals, 500, (ps, c) -> {
            ps.setString(1, c.canonicalId());
            ps.setString(2, c.name());
            ps.setDouble(3, c.lat());
            ps.setDouble(4, c.lon());
            ps.setString(5, c.municipality());
            ps.setString(6, c.ibgeCode());
            ps.setString(7, c.primarySource());
            ps.setInt(8, c.nAliases());
            ps.setString(9, c.ruleVersion());
        });
        jdbc.batchUpdate("""
                INSERT INTO station_alias (source, station_id, canonical_id, source_name, distance_m, name_similarity,
                                           rule_version) VALUES (?, ?, ?, ?, ?, ?, ?)
                """, aliases, 500, (ps, a) -> {
            ps.setString(1, a.source());
            ps.setString(2, a.stationId());
            ps.setString(3, a.canonicalId());
            ps.setString(4, a.sourceName());
            ps.setDouble(5, a.distanceM());
            ps.setObject(6, a.nameSimilarity());
            ps.setString(7, a.ruleVersion());
        });
    }

    /**
     * Horas de chuva com valor de uma estação de origem. Uma consulta por estação: a view horária calcula o lag por
     * série, e só o filtro por fonte e estação desce até ela.
     */
    public List<HourValue> hourly(String source, String stationId) {
        return jdbc.query("""
                SELECT hour_end_utc, value FROM observation_hourly_v
                WHERE source = ? AND station_id = ? AND variable = 'precipitation' AND value IS NOT NULL
                ORDER BY hour_end_utc
                """, (rs, i) -> new HourValue(rs.getObject(1, OffsetDateTime.class).toInstant(), rs.getDouble(2)),
                source, stationId);
    }

    public void insertDivergences(List<DivergenceRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO station_hour_divergence (canonical_id, hour_end_utc, kept_source, kept_station_id, kept_value,
                       other_source, other_station_id, other_value, diff_mm, tolerance_mm, rule_version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """, rows, 500, (ps, d) -> {
            ps.setString(1, d.canonicalId());
            ps.setObject(2, utc(d.hourEndUtc()));
            ps.setString(3, d.keptSource());
            ps.setString(4, d.keptStationId());
            ps.setDouble(5, d.keptValue());
            ps.setString(6, d.otherSource());
            ps.setString(7, d.otherStationId());
            ps.setDouble(8, d.otherValue());
            ps.setDouble(9, d.diffMm());
            ps.setDouble(10, d.toleranceMm());
            ps.setString(11, d.ruleVersion());
        });
    }

    public List<CanonicalView> canonicals() {
        return jdbc.query("SELECT canonical_id, name, lat, lon, municipality, ibge_code, primary_source, aliases "
                        + "FROM station_canonical_v ORDER BY canonical_id",
                (rs, i) -> new CanonicalView(rs.getString(1), rs.getString(2), rs.getDouble(3), rs.getDouble(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), strings(rs.getArray(8))));
    }

    /** Apelido → canônica, para casar o código que a PED devolve nos acumulados. */
    public Map<String, String> canonicalByStationId(String source) {
        return jdbc.query("SELECT station_id, canonical_id FROM station_alias WHERE source = ?",
                rs -> {
                    Map<String, String> m = new java.util.HashMap<>();
                    while (rs.next()) m.put(rs.getString(1), rs.getString(2));
                    return m;
                }, source);
    }

    /**
     * Instante da última leitura de chuva gravada por canônica, entre todos os apelidos, a partir de {@code since}.
     * Filtra por {@code ts_utc} para usar o índice da hypertable.
     */
    public Map<String, Instant> lastReadingByCanonical(Instant since) {
        return jdbc.query("""
                SELECT a.canonical_id, max(o.ts_utc) FROM observation o
                JOIN station_alias a ON a.source = o.source AND a.station_id = o.station_id
                WHERE o.ts_utc >= ? AND o.variable = 'precipitation' AND o.value IS NOT NULL
                GROUP BY a.canonical_id
                """, rs -> {
            Map<String, Instant> m = new java.util.HashMap<>();
            while (rs.next()) m.put(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant());
            return m;
        }, utc(since));
    }

    private static List<String> strings(Array a) throws java.sql.SQLException {
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }
}
