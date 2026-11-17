package com.projeto.mapi.repository.analise;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Leitura dos blocos de chuva para a série de 10 min do ponto (item 1.4.1). Mesma fonte da série horária e da previsão:
 * {@code canonical_rain_block_v} (PED quando há, senão a coleta ao vivo) e o vínculo de {@code point_rain_neighbors_v}.
 * A qualificação 0002 já está fora (quarentena no pipeline, ADR 0021).
 */
@Repository
@RequiredArgsConstructor
public class PointRain10MinRepository {

    /** Bloco com o fim em ts; {@code prevTs} = leitura anterior da mesma estação canônica (para o tamanho do bloco ao vivo). */
    public record Block(String canonicalId, double lat, double lon, double km, String source, String stationId,
                        Instant ts, Instant intervalStart, Instant prevTs, double mm, Instant receivedAt, Instant ingestedAt) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    private static Instant inst(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    /**
     * Blocos com fim em (from, to] das estações canônicas a até {@code maxKm} do ponto (5 km da regra + 6 km da vizinhança
     * do pico isolado). A leitura anterior vem de uma janela 2 h maior, para o primeiro bloco também ter tamanho.
     */
    public List<Block> blocks(long floodPointId, Instant from, Instant to, double maxKm) {
        return blocks(floodPointId, from, to, maxKm, true);
    }

    /** {@code withReceipt=false}: sem os instantes de recebimento (exportação do histórico, mais leve). */
    public List<Block> blocks(long floodPointId, Instant from, Instant to, double maxKm, boolean withReceipt) {
        String lateral = withReceipt ? LATERAL : "LEFT JOIN LATERAL (SELECT NULL::timestamptz AS collected_at, NULL::timestamptz AS ingested_at) r ON true";
        return jdbc.query("""
                WITH b AS (
                    SELECT n.canonical_id, n.lat, n.lon, n.km, cb.source, cb.station_id, cb.ts_utc, cb.interval_start_utc, cb.value,
                           lag(cb.ts_utc) OVER (PARTITION BY n.canonical_id ORDER BY cb.ts_utc) AS prev_ts
                    FROM point_rain_neighbors_v n
                    JOIN canonical_rain_block_v cb ON cb.canonical_id = n.canonical_id
                    WHERE n.flood_point_id = ? AND n.km <= ? AND cb.ts_utc > ? AND cb.ts_utc <= ?
                )
                SELECT b.canonical_id, b.lat, b.lon, b.km, b.source, b.station_id, b.ts_utc, b.interval_start_utc, b.prev_ts, b.value,
                       r.collected_at, r.ingested_at
                FROM b
                %s
                WHERE b.ts_utc > ?
                ORDER BY b.ts_utc, b.canonical_id
                """.formatted(lateral), (rs, i) -> new Block(rs.getString(1), rs.getDouble(2), rs.getDouble(3), rs.getDouble(4), rs.getString(5),
                rs.getString(6), inst(rs.getTimestamp(7)), inst(rs.getTimestamp(8)), inst(rs.getTimestamp(9)), rs.getDouble(10),
                inst(rs.getTimestamp(11)), inst(rs.getTimestamp(12))),
                floodPointId, maxKm, utc(from.minusSeconds(7200)), utc(to), utc(from));
    }

    private static final String LATERAL = """
            LEFT JOIN LATERAL (
                SELECT ro.collected_at, ro.ingested_at FROM observation_current oc
                JOIN raw_observation ro ON ro.content_hash = oc.raw_hash AND ro.source = oc.source
                WHERE oc.source = b.source AND oc.station_id = b.station_id AND oc.variable = 'precipitation' AND oc.ts_utc = b.ts_utc
                ORDER BY ro.collected_at LIMIT 1
            ) r ON true""";

    /** Status da checagem mensal por (estação canônica, mês UTC), pela estação PED do apelido (ADR 0022). */
    public Map<String, String> monthStatus(List<String> canonicalIds, Instant from, Instant to) {
        Map<String, String> out = new HashMap<>();
        if (canonicalIds.isEmpty()) return out;
        String in = String.join(",", canonicalIds.stream().map(c -> "?").toList());
        Object[] args = new Object[canonicalIds.size() + 2];
        for (int k = 0; k < canonicalIds.size(); k++) args[k] = canonicalIds.get(k);
        args[canonicalIds.size()] = utc(from.minusSeconds(32L * 86400));
        args[canonicalIds.size() + 1] = utc(to);
        jdbc.query("SELECT a.canonical_id, c.month_start_utc, c.status FROM station_month_check c "
                + "JOIN station_alias a ON a.source = c.source AND a.station_id = c.station_id "
                + "WHERE a.canonical_id IN (" + in + ") AND c.month_start_utc >= ? AND c.month_start_utc <= ?", rs -> {
            out.put(key(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant()), rs.getString(3));
        }, args);
        return out;
    }

    public static String key(String canonicalId, Instant monthStart) {
        return canonicalId + "|" + monthStart;
    }
}
