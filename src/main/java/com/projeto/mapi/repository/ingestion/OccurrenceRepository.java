package com.projeto.mapi.repository.ingestion;

import com.projeto.mapi.service.ingestion.occurrence.LabelFilterConfig;
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
import java.util.Optional;
import java.util.UUID;

/** flood_occurrence, occurrence_point_match, label_filter e linkage_config (V6). JDBC puro. */
@Repository
@RequiredArgsConstructor
public class OccurrenceRepository {

    /** Ocorrência a gravar (já classificada). */
    public record OccurrenceRow(String packageId, String resourceId, String recordId, String rawHash, UUID runId,
                                String address, Instant from, Instant to, String precision, String cobradeCode,
                                String cobradeText, Double lat, Double lon, String geocodeMethod) {}

    /** Ocorrência já gravada, para o casamento. */
    public record GeocodedOccurrence(long id, double lat, double lon) {}

    /** Estado gravado de uma ocorrência (raw_hash e coordenada) para evitar geocodificar de novo. */
    public record Stored(String rawHash, Double lat, Double lon, String geocodeMethod) {}

    private final JdbcTemplate jdbc;

    public Optional<LabelFilterConfig> labelFilter(String name) {
        return jdbc.query("SELECT id, name, accepted_codes, accepted_texts FROM label_filter WHERE name = ? "
                        + "ORDER BY id DESC LIMIT 1", (rs, i) -> new LabelFilterConfig(rs.getLong(1), rs.getString(2),
                        list(rs.getArray(3)), list(rs.getArray(4))), name).stream().findFirst();
    }

    private static List<String> list(Array a) throws java.sql.SQLException {
        return a == null ? List.of() : Arrays.asList((String[]) a.getArray());
    }

    /** Reutiliza o linkage_config do raio ou cria um novo (id é gravado em cada occurrence_point_match). */
    public long findOrCreateOccurrenceConfig(double radiusM, String createdBy, String note) {
        List<Long> ids = jdbc.query("SELECT id FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' "
                + "AND radius_m = ? AND method = 'NEAREST_WITHIN_RADIUS' ORDER BY id LIMIT 1",
                (rs, i) -> rs.getLong(1), radiusM);
        if (!ids.isEmpty()) return ids.get(0);
        return jdbc.queryForObject("INSERT INTO linkage_config (kind, radius_m, method, created_by, note) "
                + "VALUES ('OCCURRENCE_POINT', ?, 'NEAREST_WITHIN_RADIUS', ?, ?) RETURNING id", Long.class,
                radiusM, createdBy, note);
    }

    public Map<String, Stored> stored(String resourceId, List<String> recordIds) {
        Map<String, Stored> out = new java.util.HashMap<>();
        if (recordIds.isEmpty()) return out;
        jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT ckan_record_id, raw_hash, geocoded_lat, geocoded_lon, geocode_method "
                    + "FROM flood_occurrence WHERE ckan_resource_id = ? AND ckan_record_id = ANY(?)");
            ps.setString(1, resourceId);
            ps.setArray(2, con.createArrayOf("text", recordIds.toArray(String[]::new)));
            return ps;
        }, rs -> {
            out.put(rs.getString(1), new Stored(rs.getString(2), rs.getObject(3, Double.class),
                    rs.getObject(4, Double.class), rs.getString(5)));
        });
        return out;
    }

    /** Insere ou, se o bruto mudou, atualiza e incrementa a revisão. Devolve 1 se gravou, 0 se inalterado. */
    public int upsert(OccurrenceRow r) {
        return jdbc.update("""
                INSERT INTO flood_occurrence (ckan_package_id, ckan_resource_id, ckan_record_id, raw_hash, run_id,
                  address_text, occurred_from, occurred_to, temporal_precision, cobrade_code, cobrade_text,
                  geocoded_lat, geocoded_lon, geocode_method)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (ckan_resource_id, ckan_record_id) DO UPDATE SET
                  raw_hash = EXCLUDED.raw_hash, run_id = EXCLUDED.run_id, address_text = EXCLUDED.address_text,
                  occurred_from = EXCLUDED.occurred_from, occurred_to = EXCLUDED.occurred_to,
                  temporal_precision = EXCLUDED.temporal_precision, cobrade_code = EXCLUDED.cobrade_code,
                  cobrade_text = EXCLUDED.cobrade_text, geocoded_lat = EXCLUDED.geocoded_lat,
                  geocoded_lon = EXCLUDED.geocoded_lon, geocode_method = EXCLUDED.geocode_method,
                  revision = flood_occurrence.revision + 1
                WHERE flood_occurrence.raw_hash <> EXCLUDED.raw_hash
                """, ps -> {
            ps.setString(1, r.packageId());
            ps.setString(2, r.resourceId());
            ps.setString(3, r.recordId());
            ps.setString(4, r.rawHash());
            ps.setObject(5, r.runId());
            ps.setString(6, r.address());
            ps.setObject(7, OffsetDateTime.ofInstant(r.from(), ZoneOffset.UTC));
            ps.setObject(8, OffsetDateTime.ofInstant(r.to(), ZoneOffset.UTC));
            ps.setString(9, r.precision());
            ps.setString(10, r.cobradeCode());
            ps.setString(11, r.cobradeText());
            ps.setObject(12, r.lat());
            ps.setObject(13, r.lon());
            ps.setString(14, r.geocodeMethod());
        });
    }

    public List<GeocodedOccurrence> geocoded() {
        return jdbc.query("SELECT id, geocoded_lat, geocoded_lon FROM flood_occurrence "
                + "WHERE geocoded_lat IS NOT NULL AND geocoded_lon IS NOT NULL ORDER BY id",
                (rs, i) -> new GeocodedOccurrence(rs.getLong(1), rs.getDouble(2), rs.getDouble(3)));
    }

    /** Grava o casamento; o mesmo (ocorrência, ponto, config) não duplica. Devolve 1 se inseriu. */
    public int insertMatch(long occurrenceId, long pointId, double distanceM, double radiusM, long configId,
                           String ruleVersion, Instant matchedAt) {
        return jdbc.update("""
                INSERT INTO occurrence_point_match (occurrence_id, flood_point_id, distance_m, radius_m,
                  linkage_config_id, method, rule_version, matched_at)
                VALUES (?, ?, ?, ?, ?, 'NEAREST_WITHIN_RADIUS', ?, ?)
                ON CONFLICT ON CONSTRAINT occurrence_match_unique DO NOTHING
                """, occurrenceId, pointId, distanceM, radiusM, configId, ruleVersion,
                OffsetDateTime.ofInstant(matchedAt, ZoneOffset.UTC));
    }
}
