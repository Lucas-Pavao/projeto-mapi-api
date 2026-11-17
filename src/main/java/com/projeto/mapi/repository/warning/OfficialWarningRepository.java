package com.projeto.mapi.repository.warning;

import com.projeto.mapi.dto.OfficialWarningDTO;
import com.projeto.mapi.service.warning.InmetWarningParser.ParsedWarning;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Persistência JDBC dos avisos meteorológicos oficiais (V31). */
@Repository
@RequiredArgsConstructor
public class OfficialWarningRepository {

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /**
     * Insere ou atualiza avisos mantendo {@code first_seen_at} intacto no conflito (proxy de emissão).
     * Retorna quantos registros foram tocados.
     */
    public int upsertAll(String source, UUID runId, Instant seenAt, List<ParsedWarning> warnings) {
        if (warnings.isEmpty()) return 0;
        OffsetDateTime seen = utc(seenAt);
        int touched = 0;
        for (ParsedWarning w : warnings) {
            touched += jdbc.update(con -> {
                var ps = con.prepareStatement("""
                        INSERT INTO official_warning (
                            source, warning_id, sequence_id, external_id, cap_code,
                            first_seen_at, last_seen_at, valid_from, valid_to,
                            severity, severity_id, color_hex, description,
                            risks, instructions, geocodes, municipalities, polygon_geojson,
                            altered, closed, raw_hash, run_id
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?)
                        ON CONFLICT (source, warning_id, sequence_id) DO UPDATE SET
                            last_seen_at = EXCLUDED.last_seen_at,
                            external_id = EXCLUDED.external_id,
                            cap_code = EXCLUDED.cap_code,
                            valid_from = EXCLUDED.valid_from,
                            valid_to = EXCLUDED.valid_to,
                            severity = EXCLUDED.severity,
                            severity_id = EXCLUDED.severity_id,
                            color_hex = EXCLUDED.color_hex,
                            description = EXCLUDED.description,
                            risks = EXCLUDED.risks,
                            instructions = EXCLUDED.instructions,
                            geocodes = EXCLUDED.geocodes,
                            municipalities = EXCLUDED.municipalities,
                            polygon_geojson = EXCLUDED.polygon_geojson,
                            altered = EXCLUDED.altered,
                            closed = EXCLUDED.closed,
                            raw_hash = EXCLUDED.raw_hash,
                            run_id = EXCLUDED.run_id
                        """);
                ps.setString(1, source);
                ps.setInt(2, w.warningId());
                ps.setInt(3, w.sequenceId());
                ps.setObject(4, w.externalId(), Types.INTEGER);
                ps.setString(5, w.capCode());
                ps.setObject(6, seen);
                ps.setObject(7, seen);
                ps.setObject(8, utc(w.validFrom()));
                ps.setObject(9, utc(w.validTo()));
                ps.setString(10, w.severity());
                ps.setObject(11, w.severityId(), Types.INTEGER);
                ps.setString(12, w.colorHex());
                ps.setString(13, w.description());
                ps.setArray(14, con.createArrayOf("text", w.risks().toArray()));
                ps.setArray(15, con.createArrayOf("text", w.instructions().toArray()));
                ps.setArray(16, con.createArrayOf("text", w.geocodes().toArray()));
                ps.setString(17, w.municipalities());
                ps.setString(18, w.polygonGeoJson());
                ps.setBoolean(19, w.altered());
                ps.setBoolean(20, w.closed());
                ps.setString(21, w.rawHash());
                ps.setObject(22, runId);
                return ps;
            });
        }
        return touched;
    }

    /** Avisos vigentes em {@code now} (não encerrados e com {@code valid_to > now}). */
    public List<OfficialWarningDTO> findActive(Instant now, String ibgeFilter, String municipalityPattern) {
        OffsetDateTime ref = utc(now);
        if (ibgeFilter != null) {
            return jdbc.query("""
                    SELECT warning_id, sequence_id, external_id, cap_code, source,
                           first_seen_at, last_seen_at, valid_from, valid_to,
                           severity, severity_id, color_hex, description,
                           risks, instructions, geocodes, municipalities, altered, closed
                    FROM official_warning
                    WHERE NOT closed AND valid_to > ? AND ? = ANY(geocodes)
                    ORDER BY severity_id DESC NULLS LAST, valid_from ASC, warning_id ASC
                    """, this::mapDto, ref, ibgeFilter);
        }
        if (municipalityPattern != null) {
            return jdbc.query("""
                    SELECT warning_id, sequence_id, external_id, cap_code, source,
                           first_seen_at, last_seen_at, valid_from, valid_to,
                           severity, severity_id, color_hex, description,
                           risks, instructions, geocodes, municipalities, altered, closed
                    FROM official_warning
                    WHERE NOT closed AND valid_to > ? AND lower(coalesce(municipalities, '')) LIKE ?
                    ORDER BY severity_id DESC NULLS LAST, valid_from ASC, warning_id ASC
                    """, this::mapDto, ref, "%" + municipalityPattern.toLowerCase(java.util.Locale.ROOT) + "%");
        }
        return jdbc.query("""
                SELECT warning_id, sequence_id, external_id, cap_code, source,
                       first_seen_at, last_seen_at, valid_from, valid_to,
                       severity, severity_id, color_hex, description,
                       risks, instructions, geocodes, municipalities, altered, closed
                FROM official_warning
                WHERE NOT closed AND valid_to > ?
                ORDER BY severity_id DESC NULLS LAST, valid_from ASC, warning_id ASC
                """, this::mapDto, ref);
    }

    private OfficialWarningDTO mapDto(ResultSet rs, int rowNum) throws SQLException {
        return new OfficialWarningDTO(
                rs.getInt("warning_id"),
                rs.getInt("sequence_id"),
                rs.getObject("external_id", Integer.class),
                rs.getString("cap_code"),
                rs.getString("source"),
                rs.getObject("first_seen_at", OffsetDateTime.class).toInstant(),
                rs.getObject("last_seen_at", OffsetDateTime.class).toInstant(),
                rs.getObject("valid_from", OffsetDateTime.class).toInstant(),
                rs.getObject("valid_to", OffsetDateTime.class).toInstant(),
                rs.getString("severity"),
                rs.getObject("severity_id", Integer.class),
                rs.getString("color_hex"),
                rs.getString("description"),
                textArray(rs.getArray("risks")),
                textArray(rs.getArray("instructions")),
                textArray(rs.getArray("geocodes")),
                rs.getString("municipalities"),
                rs.getBoolean("altered"),
                rs.getBoolean("closed")
        );
    }

    private static List<String> textArray(Array sqlArray) throws SQLException {
        if (sqlArray == null) return List.of();
        String[] values = (String[]) sqlArray.getArray();
        return values == null ? List.of() : Arrays.asList(values);
    }
}
