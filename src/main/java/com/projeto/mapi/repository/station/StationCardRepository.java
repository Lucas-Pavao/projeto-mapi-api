package com.projeto.mapi.repository.station;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Leituras do card do pluviômetro: blocos brutos, última leitura e quarentena do dia por estação canônica. */
@Repository
@RequiredArgsConstructor
public class StationCardRepository {

    /** Um bloco de chuva: {@code intervalStartUtc} nulo quando a fonte não declara a duração. */
    public record BlockRow(String source, Instant tsUtc, Instant intervalStartUtc, double value) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Pares (source, station_id) de apelidos {@code SOURCE:id} como cláusula e argumentos. */
    private static String pairs(List<String> aliases, List<Object> args) {
        StringBuilder sb = new StringBuilder();
        for (String a : aliases) {
            int k = a.indexOf(':');
            if (sb.length() > 0) sb.append(", ");
            sb.append("(?, ?)");
            args.add(a.substring(0, k));
            args.add(a.substring(k + 1));
        }
        return sb.toString();
    }

    /** Blocos de chuva vigentes ({@code observation_current}) com carimbo em (from, to]; filtra por {@code ts_utc} (hypertable). */
    public List<BlockRow> blocks(List<String> aliases, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        String in = pairs(aliases, args);
        args.add(utc(from));
        args.add(utc(to));
        return jdbc.query("SELECT source, ts_utc, interval_start_utc, value FROM observation_current "
                        + "WHERE (source, station_id) IN (" + in + ") AND variable = 'precipitation' AND value IS NOT NULL "
                        + "AND ts_utc > ? AND ts_utc <= ? ORDER BY ts_utc",
                (rs, i) -> {
                    OffsetDateTime start = rs.getObject(3, OffsetDateTime.class);
                    return new BlockRow(rs.getString(1), rs.getObject(2, OffsetDateTime.class).toInstant(),
                            start == null ? null : start.toInstant(), rs.getDouble(4));
                }, args.toArray());
    }

    /** Última leitura de chuva (qualquer apelido) com carimbo em (since, until]. */
    public Optional<Instant> lastReading(List<String> aliases, Instant since, Instant until) {
        List<Object> args = new ArrayList<>();
        String in = pairs(aliases, args);
        args.add(utc(since));
        args.add(utc(until));
        return jdbc.query("SELECT max(ts_utc) FROM observation_current WHERE (source, station_id) IN (" + in
                        + ") AND variable = 'precipitation' AND value IS NOT NULL AND ts_utc > ? AND ts_utc <= ?",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class), args.toArray()).stream()
                .filter(java.util.Objects::nonNull).map(OffsetDateTime::toInstant).findFirst();
    }

    /** Linhas ABERTAS da quarentena por motivo, postas em quarentena em [from, to), somando os apelidos. */
    public Map<String, Integer> openQuarantine(List<String> aliases, Instant from, Instant to) {
        List<Object> args = new ArrayList<>();
        String in = pairs(aliases, args);
        args.add(utc(from));
        args.add(utc(to));
        return jdbc.query("SELECT q.reason, count(*) FROM ingestion_quarantine q "
                        + "JOIN raw_observation r ON r.id = q.raw_id AND r.ingested_at = q.raw_ingested_at "
                        + "WHERE (r.source, r.station_id) IN (" + in + ") AND q.resolved_at IS NULL "
                        + "AND q.quarantined_at >= ? AND q.quarantined_at < ? GROUP BY q.reason",
                rs -> {
                    Map<String, Integer> m = new HashMap<>();
                    while (rs.next()) m.put(rs.getString(1), rs.getInt(2));
                    return m;
                }, args.toArray());
    }

    /** Distância (m) do apelido da fonte até a estação canônica; vazio se a fonte não tem apelido. */
    public Optional<Double> aliasDistanceM(String canonicalId, String source) {
        return jdbc.query("SELECT distance_m FROM station_alias WHERE canonical_id = ? AND source = ? ORDER BY distance_m LIMIT 1",
                (rs, i) -> rs.getDouble(1), canonicalId, source).stream().findFirst();
    }
}
