package com.projeto.mapi.repository.ingestion;

import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClusterer;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Grupos de deduplicação entre fontes e inventário de rótulos (V23, ADR 0019). */
@Repository
@RequiredArgsConstructor
public class OccurrenceClusterRepository {

    private final JdbcTemplate jdbc;

    /** Ocorrências canônicas (uma por registro de origem, como no rótulo do export). */
    public List<OccurrenceClusterer.Occurrence> canonicalOccurrences() {
        return jdbc.query("""
                SELECT o.id, o.source, o.event_type, o.occurred_from, o.geocoded_lat, o.geocoded_lon,
                       o.temporal_precision, o.spatial_precision, o.confidence
                FROM flood_occurrence o WHERE
                """ + " " + OccurrenceRepository.CANONICAL + " ORDER BY o.id",
                (rs, i) -> new OccurrenceClusterer.Occurrence(rs.getLong(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getObject(5, Double.class),
                        rs.getObject(6, Double.class), rs.getString(7), rs.getString(8), rs.getString(9)));
    }

    /** Apaga os grupos (são derivados) e grava os novos. */
    public void replaceAll(List<OccurrenceClusterer.Cluster> clusters, String ruleVersion) {
        jdbc.update("DELETE FROM occurrence_cluster");
        for (OccurrenceClusterer.Cluster c : clusters) {
            Long id = jdbc.queryForObject("""
                    INSERT INTO occurrence_cluster (local_day, lat, lon, event_type, temporal_precision,
                        spatial_precision, confidence, first_from, sources, members, rule_version)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                    """, Long.class, java.sql.Date.valueOf(c.localDay()), c.lat(), c.lon(), c.eventType(),
                    c.temporalPrecision(), c.spatialPrecision(), c.confidence(), Timestamp.from(c.firstFrom()),
                    c.sources().toArray(new String[0]), c.memberIds().size(), ruleVersion);
            jdbc.batchUpdate("INSERT INTO occurrence_cluster_member (cluster_id, occurrence_id) VALUES (?, ?)",
                    c.memberIds(), c.memberIds().size(), (ps, m) -> { ps.setLong(1, id); ps.setLong(2, m); });
        }
    }

    /** Contagem das ocorrências canônicas por uma coluna (source, temporal_precision, confidence...). */
    public Map<String, Integer> countBy(String column) {
        if (!List.of("source", "temporal_precision", "confidence", "spatial_precision", "event_type").contains(column))
            throw new IllegalArgumentException(column);
        Map<String, Integer> out = new LinkedHashMap<>();
        jdbc.query("SELECT coalesce(" + column + ", 'NULL'), count(*) FROM flood_occurrence o WHERE "
                + OccurrenceRepository.CANONICAL + " GROUP BY 1 ORDER BY 1",
                rs -> { out.put(rs.getString(1), rs.getInt(2)); });
        return out;
    }

    public record ClusterTotals(int clusters, int days, int daysWithHour, int daysAlta) {}

    public ClusterTotals clusterTotals() {
        return jdbc.queryForObject("""
                SELECT count(*), count(DISTINCT local_day),
                       count(DISTINCT local_day) FILTER (WHERE temporal_precision IN ('HOUR', 'MINUTE')),
                       count(DISTINCT local_day) FILTER (WHERE confidence = 'ALTA')
                FROM occurrence_cluster
                """, (rs, i) -> new ClusterTotals(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
    }

    public record PointCount(String slug, int occurrences, int days, int daysWithHour) {}

    /** Por ponto, no linkage dado: ocorrências casadas e dias (de grupo) com e sem hora. */
    public List<PointCount> perPoint(long linkageConfigId) {
        return jdbc.query("""
                SELECT fp.slug, count(DISTINCT m.occurrence_id),
                       count(DISTINCT c.local_day),
                       count(DISTINCT c.local_day) FILTER (WHERE c.temporal_precision IN ('HOUR', 'MINUTE'))
                FROM flood_points fp
                LEFT JOIN occurrence_point_match m ON m.flood_point_id = fp.id AND m.linkage_config_id = ?
                LEFT JOIN occurrence_cluster_member cm ON cm.occurrence_id = m.occurrence_id
                LEFT JOIN occurrence_cluster c ON c.id = cm.cluster_id
                GROUP BY fp.slug ORDER BY fp.slug
                """, (rs, i) -> new PointCount(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)),
                linkageConfigId);
    }
}
