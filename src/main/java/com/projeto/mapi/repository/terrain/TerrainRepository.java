package com.projeto.mapi.repository.terrain;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlTypeValue;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Acesso a terrain_run, point_terrain e point_terrain_curve (V9, ADR 0008). */
@Repository
@RequiredArgsConstructor
public class TerrainRepository {

    public record RunRow(long id, OffsetDateTime runAt, String vrtSha256, String scriptSha256, short flightYear,
                         String verticalReference) {}

    public record NewRun(String vrtSha256, String scriptSha256, String gdalImageDigest, String softwareVersionsJson,
                         String[] sheets, String parametersJson, short flightYear, String verticalReference,
                         String verticalReferenceAdr) {}

    public record NewPoint(long floodPointId, long runId, String status, String sourceSheet, Double streetElevationM,
                           Short elevationPixelCount, Double meanSlopePct, Double maxSlopePct, Double depressionDepthM,
                           Double contributingAreaM2, Double bottomElevationM, Double bottomDistanceM,
                           String diagnosticsJson) {}

    public record CurveRow(int aboveBottomCm, double elevationM, double cumulativeVolumeM3, Double m3PerCm,
                           Double wetAreaM2) {}

    public record PointRow(long id, String slug, long runId, String status, String sourceSheet, Double streetElevationM,
                           Double meanSlopePct, Double maxSlopePct, Double depressionDepthM, Double contributingAreaM2,
                           Double bottomElevationM, Double bottomDistanceM, String diagnosticsJson) {}

    private final JdbcTemplate jdbc;

    /** Serializa cargas concorrentes até o fim da transação. */
    public void lockLoad() {
        jdbc.queryForList("SELECT pg_advisory_xact_lock(9008)");
    }

    public Optional<Long> findRunByInputHash(String inputSha256) {
        return jdbc.queryForList("SELECT id FROM terrain_run WHERE parameters ->> 'inputSha256' = ?", Long.class,
                inputSha256).stream().findFirst();
    }

    /** Insere a execução e a torna a vigente (a anterior deixa de ser). */
    public long insertCurrentRun(NewRun r) {
        jdbc.update("UPDATE terrain_run SET current = FALSE WHERE current");
        return jdbc.queryForObject("""
                INSERT INTO terrain_run (vrt_sha256, script_sha256, gdal_image_digest, software_versions, sheets,
                    parameters, flight_year, vertical_reference, vertical_reference_adr, current)
                VALUES (?, ?, ?, ?::jsonb, ?, ?::jsonb, ?, ?, ?, TRUE) RETURNING id
                """, Long.class, r.vrtSha256(), r.scriptSha256(), r.gdalImageDigest(), r.softwareVersionsJson(),
                new TextArray(r.sheets()), r.parametersJson(), r.flightYear(), r.verticalReference(),
                r.verticalReferenceAdr());
    }

    public Map<String, Long> floodPointIdsBySlug() {
        Map<String, Long> m = new HashMap<>();
        jdbc.query("SELECT slug, id FROM flood_points", rs -> { m.put(rs.getString(1), rs.getLong(2)); });
        return m;
    }

    public long insertPoint(NewPoint p) {
        return jdbc.queryForObject("""
                INSERT INTO point_terrain (flood_point_id, run_id, status, source_sheet, street_elevation_m,
                    elevation_pixel_count, mean_slope_pct, max_slope_pct, depression_depth_m, contributing_area_m2,
                    bottom_elevation_m, bottom_distance_m, diagnostics)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb) RETURNING id
                """, Long.class, p.floodPointId(), p.runId(), p.status(), p.sourceSheet(), p.streetElevationM(),
                p.elevationPixelCount(), p.meanSlopePct(), p.maxSlopePct(), p.depressionDepthM(),
                p.contributingAreaM2(), p.bottomElevationM(), p.bottomDistanceM(), p.diagnosticsJson());
    }

    public void insertCurve(long pointTerrainId, List<CurveRow> rows) {
        jdbc.batchUpdate("""
                INSERT INTO point_terrain_curve (point_terrain_id, above_bottom_cm, elevation_m, cumulative_volume_m3,
                    m3_per_cm, wet_area_m2) VALUES (?, ?, ?, ?, ?, ?)
                """, rows, rows.size(), (ps, c) -> {
            ps.setLong(1, pointTerrainId);
            ps.setInt(2, c.aboveBottomCm());
            ps.setDouble(3, c.elevationM());
            ps.setDouble(4, c.cumulativeVolumeM3());
            if (c.m3PerCm() == null) ps.setNull(5, Types.DOUBLE); else ps.setDouble(5, c.m3PerCm());
            if (c.wetAreaM2() == null) ps.setNull(6, Types.DOUBLE); else ps.setDouble(6, c.wetAreaM2());
        });
    }

    public Optional<RunRow> currentRun() {
        return jdbc.query("""
                SELECT id, run_at, vrt_sha256, script_sha256, flight_year, vertical_reference
                FROM terrain_run WHERE current
                """, (rs, i) -> new RunRow(rs.getLong("id"), rs.getObject("run_at", OffsetDateTime.class),
                rs.getString("vrt_sha256"), rs.getString("script_sha256"), rs.getShort("flight_year"),
                rs.getString("vertical_reference"))).stream().findFirst();
    }

    /** Terreno do ponto na execução vigente; vazio se o ponto não tem registro nela. */
    public Optional<PointRow> findCurrentPoint(String slug) {
        return pointsOfCurrentRun("WHERE fp.slug = ?", slug).stream().findFirst();
    }

    /** Todos os pontos da execução vigente (para o export). */
    public List<PointRow> allCurrentPoints() {
        return pointsOfCurrentRun("");
    }

    public List<CurveRow> curve(long pointTerrainId) {
        return jdbc.query("""
                SELECT above_bottom_cm, elevation_m, cumulative_volume_m3, m3_per_cm, wet_area_m2
                FROM point_terrain_curve WHERE point_terrain_id = ? ORDER BY above_bottom_cm
                """, (rs, i) -> new CurveRow(rs.getInt(1), rs.getDouble(2), rs.getDouble(3),
                rs.getObject(4, Double.class), rs.getObject(5, Double.class)), pointTerrainId);
    }

    private List<PointRow> pointsOfCurrentRun(String where, Object... args) {
        return jdbc.query("""
                SELECT pt.id, fp.slug, pt.run_id, pt.status, pt.source_sheet, pt.street_elevation_m, pt.mean_slope_pct,
                       pt.max_slope_pct, pt.depression_depth_m, pt.contributing_area_m2, pt.bottom_elevation_m,
                       pt.bottom_distance_m, pt.diagnostics::text AS diagnostics
                FROM point_terrain pt
                JOIN terrain_run tr ON tr.id = pt.run_id AND tr.current
                JOIN flood_points fp ON fp.id = pt.flood_point_id
                """ + where + " ORDER BY fp.slug", (rs, i) -> new PointRow(rs.getLong("id"), rs.getString("slug"),
                rs.getLong("run_id"), rs.getString("status"), rs.getString("source_sheet"),
                rs.getObject("street_elevation_m", Double.class), rs.getObject("mean_slope_pct", Double.class),
                rs.getObject("max_slope_pct", Double.class), rs.getObject("depression_depth_m", Double.class),
                rs.getObject("contributing_area_m2", Double.class), rs.getObject("bottom_elevation_m", Double.class),
                rs.getObject("bottom_distance_m", Double.class), rs.getString("diagnostics")), args);
    }

    /** Valor text[] para o driver. */
    private record TextArray(String[] values) implements SqlTypeValue {
        @Override
        public void setTypeValue(PreparedStatement ps, int index, int sqlType, String typeName) throws SQLException {
            ps.setArray(index, ps.getConnection().createArrayOf("text", values));
        }
    }
}
