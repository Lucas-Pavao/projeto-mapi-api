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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** flood_occurrence, occurrence_point_match, label_filter e linkage_config (V6). JDBC puro. */
@Repository
@RequiredArgsConstructor
public class OccurrenceRepository {

    /** Ocorrência a gravar (já classificada). */
    public record OccurrenceRow(String packageId, String resourceId, String recordId, String rawHash, UUID runId,
                                String address, Instant from, Instant to, String precision, String cobradeCode,
                                String cobradeText, Double lat, Double lon, String geocodeMethod, String geocodeLevel,
                                String neighborhood) {}

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

    /** linkage_config de ocorrência usado no rótulo do export. */
    public record LinkageRef(long id, double radiusM, String method) {}

    /** O config de id dado (se for OCCURRENCE_POINT) ou, sem id, o de menor id com o raio dado. */
    public Optional<LinkageRef> occurrenceLinkage(Long id, double radiusM) {
        var map = (org.springframework.jdbc.core.RowMapper<LinkageRef>) (rs, i) ->
                new LinkageRef(rs.getLong(1), rs.getDouble(2), rs.getString(3));
        return (id != null
                ? jdbc.query("SELECT id, radius_m, method FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' AND id = ?", map, id)
                : jdbc.query("SELECT id, radius_m, method FROM linkage_config WHERE kind = 'OCCURRENCE_POINT' "
                        + "AND radius_m = ? ORDER BY id LIMIT 1", map, radiusM)).stream().findFirst();
    }

    /** Ocorrência casada com um ponto numa linkage_config. */
    public record MatchedOccurrence(long id, Instant from, Instant to, boolean hour) {}

    public List<MatchedOccurrence> matchedOccurrences(long pointId, long linkageConfigId) {
        return jdbc.query("""
                SELECT o.id, o.occurred_from, o.occurred_to, o.temporal_precision
                FROM occurrence_point_match m JOIN flood_occurrence o ON o.id = m.occurrence_id
                WHERE m.flood_point_id = ? AND m.linkage_config_id = ? AND
                """ + " " + CANONICAL + """
                 ORDER BY o.occurred_from, o.id
                """, (rs, i) -> new MatchedOccurrence(rs.getLong(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                rs.getObject(3, OffsetDateTime.class).toInstant(), "HOUR".equals(rs.getString(4))),
                pointId, linkageConfigId);
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
                  geocoded_lat, geocoded_lon, geocode_method, geocode_level, neighborhood)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (ckan_resource_id, ckan_record_id) DO UPDATE SET
                  raw_hash = EXCLUDED.raw_hash, run_id = EXCLUDED.run_id, address_text = EXCLUDED.address_text,
                  occurred_from = EXCLUDED.occurred_from, occurred_to = EXCLUDED.occurred_to,
                  temporal_precision = EXCLUDED.temporal_precision, cobrade_code = EXCLUDED.cobrade_code,
                  cobrade_text = EXCLUDED.cobrade_text, geocoded_lat = EXCLUDED.geocoded_lat,
                  geocoded_lon = EXCLUDED.geocoded_lon, geocode_method = EXCLUDED.geocode_method,
                  geocode_level = EXCLUDED.geocode_level, neighborhood = EXCLUDED.neighborhood,
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
            ps.setString(15, r.geocodeLevel());
            ps.setString(16, r.neighborhood());
        });
    }

    /**
     * Preenche a coordenada de uma ocorrência que foi gravada sem ela (geocodificador indisponível na época). Só toca
     * linhas ainda sem coordenada; não mexe em raw_hash nem em revisão (o bruto não mudou).
     */
    public int fillGeocode(String resourceId, String recordId, double lat, double lon, String method, String level) {
        return jdbc.update("UPDATE flood_occurrence SET geocoded_lat = ?, geocoded_lon = ?, geocode_method = ?, "
                + "geocode_level = ? WHERE ckan_resource_id = ? AND ckan_record_id = ? AND geocoded_lat IS NULL",
                lat, lon, method, level, resourceId, recordId);
    }

    /** Ocorrência gravada cujo nível de geocodificação ainda não foi preenchido (legado anterior à V11). */
    public record PendingLevel(long id, String resourceId, String recordId, String address, Double lat, Double lon,
                               String geocodeMethod) {}

    public List<PendingLevel> pendingGeocodeLevel() {
        return jdbc.query("SELECT id, ckan_resource_id, ckan_record_id, address_text, geocoded_lat, geocoded_lon, "
                + "geocode_method FROM flood_occurrence WHERE geocode_level IS NULL ORDER BY id",
                (rs, i) -> new PendingLevel(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, Double.class), rs.getObject(6, Double.class), rs.getString(7)));
    }

    /** Preenche nível e bairro de uma ocorrência (só colunas informativas; não muda raw_hash nem revisão). */
    public int setGeocodeInfo(long id, String level, String neighborhood) {
        return jdbc.update("UPDATE flood_occurrence SET geocode_level = COALESCE(?, geocode_level), "
                + "neighborhood = COALESCE(?, neighborhood) WHERE id = ?", level, neighborhood, id);
    }

    /**
     * Identidades ({@code _id|início}) já gravadas por OUTRO recurso do mesmo CKAN. O portal publica cada ano em
     * recursos duplicados com os mesmos {@code _id}; a identidade de origem da ocorrência é {@code _id} +
     * {@code occurred_from} (o {@code _id} sozinho repete entre anos). Vale a mais antiga (menor id).
     */
    public Set<String> knownInOtherResources(String resourceId, List<String> recordIds) {
        Set<String> out = new HashSet<>();
        if (recordIds.isEmpty()) return out;
        jdbc.query(con -> {
            var ps = con.prepareStatement("SELECT ckan_record_id, occurred_from FROM flood_occurrence "
                    + "WHERE ckan_resource_id <> ? AND ckan_record_id = ANY(?)");
            ps.setString(1, resourceId);
            ps.setArray(2, con.createArrayOf("text", recordIds.toArray(String[]::new)));
            return ps;
        }, rs -> {
            out.add(rs.getString(1) + "|" + rs.getObject(2, OffsetDateTime.class).toInstant());
        });
        return out;
    }

    /** Predicado SQL: a linha é a ocorrência canônica (menor id) da sua identidade de origem. */
    static final String CANONICAL = "NOT EXISTS (SELECT 1 FROM flood_occurrence d WHERE d.ckan_record_id = o.ckan_record_id "
            + "AND d.occurred_from = o.occurred_from AND d.id < o.id)";

    public List<GeocodedOccurrence> geocoded() {
        return jdbc.query("SELECT o.id, o.geocoded_lat, o.geocoded_lon FROM flood_occurrence o "
                + "WHERE o.geocoded_lat IS NOT NULL AND o.geocoded_lon IS NOT NULL AND " + CANONICAL + " ORDER BY o.id",
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
