package com.projeto.mapi.repository.ingestion;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

/**
 * Progresso da carga do Cemaden PED por estação e janela ({@code ped_fetch_window}, V24). A marcação é feita dentro
 * da transação do lote ({@link com.projeto.mapi.service.ingestion.FetchedBatch#inTransaction()}): janela marcada
 * implica bruto e observação gravados.
 */
@Repository
@RequiredArgsConstructor
public class PedFetchWindowRepository {

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Chaves {@code estação|início|fim} (instantes ISO) das janelas já carregadas. */
    public Set<String> doneKeys() {
        Set<String> out = new HashSet<>();
        jdbc.query("SELECT station_id, window_start, window_end FROM ped_fetch_window", rs -> {
            out.add(key(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                    rs.getObject(3, OffsetDateTime.class).toInstant()));
        });
        return out;
    }

    public static String key(String station, Instant start, Instant end) {
        return station + "|" + start + "|" + end;
    }

    public void markDone(String station, Instant start, Instant end, String status, int httpStatus, int records,
                         String sha256, String url, String parserVersion, Instant fetchedAt) {
        jdbc.update("""
                INSERT INTO ped_fetch_window (station_id, window_start, window_end, status, http_status, records, sha256,
                                              url, parser_version, fetched_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (station_id, window_start, window_end) DO UPDATE SET
                  status = EXCLUDED.status, http_status = EXCLUDED.http_status, records = EXCLUDED.records,
                  sha256 = EXCLUDED.sha256, url = EXCLUDED.url, parser_version = EXCLUDED.parser_version,
                  fetched_at = EXCLUDED.fetched_at
                """, station, utc(start), utc(end), status, httpStatus, records, sha256, url, parserVersion,
                utc(fetchedAt));
    }
}
