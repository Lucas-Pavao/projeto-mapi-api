package com.projeto.mapi.repository.radar;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Manifesto das imagens do radar da APAC (V33). Uma linha por (produto, instante); nada é apagado. */
@Repository
@RequiredArgsConstructor
public class RadarImageRepository {

    public record Entry(String product, Instant tsUtc, String status, String fileName, String relPath, String sha256,
                        Integer sizeBytes, Integer httpStatus, Instant fetchedAt, String note) {}

    public record Summary(String product, String status, long count, Instant first, Instant last) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Instantes já com arquivo gravado (status OK) do produto, a partir de {@code from}. */
    public Set<Instant> stored(String product, Instant from) {
        return new HashSet<>(jdbc.query("SELECT ts_utc FROM radar_image WHERE product = ? AND status = 'OK' AND ts_utc >= ?",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class).toInstant(), product, utc(from)));
    }

    /** Instantes já registrados (qualquer status) do produto, a partir de {@code from}. */
    public Set<Instant> known(String product, Instant from) {
        return new HashSet<>(jdbc.query("SELECT ts_utc FROM radar_image WHERE product = ? AND ts_utc >= ?",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class).toInstant(), product, utc(from)));
    }

    /** Grava ou promove a linha. Um OK nunca é rebaixado para ABSENT/ERROR. */
    public void upsert(Entry e) {
        jdbc.update("""
                INSERT INTO radar_image (product, ts_utc, status, file_name, rel_path, sha256, size_bytes, http_status, fetched_at, note)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (product, ts_utc) DO UPDATE SET
                    status = EXCLUDED.status, rel_path = EXCLUDED.rel_path, sha256 = EXCLUDED.sha256,
                    size_bytes = EXCLUDED.size_bytes, http_status = EXCLUDED.http_status,
                    fetched_at = EXCLUDED.fetched_at, note = EXCLUDED.note
                WHERE radar_image.status <> 'OK'
                """, e.product(), utc(e.tsUtc()), e.status(), e.fileName(), e.relPath(), e.sha256(), e.sizeBytes(),
                e.httpStatus(), utc(e.fetchedAt()), e.note());
    }

    public List<Summary> summary() {
        return jdbc.query("""
                SELECT product, status, count(*), min(ts_utc), max(ts_utc) FROM radar_image GROUP BY 1, 2 ORDER BY 1, 2
                """, (rs, i) -> new Summary(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getObject(4, OffsetDateTime.class).toInstant(), rs.getObject(5, OffsetDateTime.class).toInstant()));
    }
}
