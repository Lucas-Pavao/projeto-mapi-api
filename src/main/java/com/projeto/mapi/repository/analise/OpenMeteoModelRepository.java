package com.projeto.mapi.repository.analise;

import com.projeto.mapi.service.weather.OpenMeteoModelClient.Hour;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Rodadas do Open-Meteo por modelo (V42). Só INSERT (ON CONFLICT DO NOTHING) e SELECT: nada é sobrescrito. */
@Repository
@RequiredArgsConstructor
public class OpenMeteoModelRepository {

    public record Summary(String model, String product, long rows, Instant firstHour, Instant lastHour, Instant lastIssued) {}

    private final JdbcTemplate jdbc;

    private static OffsetDateTime utc(Instant i) {
        return i == null ? null : OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Grava as horas; {@code issuedBefore} dá o instante de publicação de cada hora. Devolve quantas eram novas. */
    public int insertAll(long floodPointId, String model, String product, List<Hour> hours, Function<Hour, Instant> issuedBefore,
                         Instant runInit, Double gridLat, Double gridLon, String rawHash) {
        List<Object[]> args = new ArrayList<>();
        for (Hour h : hours) {
            if (h.precipMm() == null && h.cape() == null) continue;   // hora sem valor: não vira linha (nem zero)
            args.add(new Object[]{floodPointId, model, product, utc(h.hourEndUtc()), utc(issuedBefore.apply(h)), utc(runInit),
                    h.precipMm(), h.cape(), gridLat, gridLon, rawHash});
        }
        if (args.isEmpty()) return 0;
        int[] r = jdbc.batchUpdate("""
                INSERT INTO open_meteo_model_hour (flood_point_id, model, product, hour_end_utc, issued_before_utc,
                    run_init_utc, precip_mm, cape, grid_lat, grid_lon, raw_hash)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """, args);
        int n = 0;
        for (int x : r) n += Math.max(0, x);
        return n;
    }

    /** Início da rodada mais nova já gravada para o modelo (rodada ao vivo). */
    public Instant lastRunInit(String model) {
        Timestamp t = jdbc.queryForObject("SELECT max(run_init_utc) FROM open_meteo_model_hour WHERE model = ? AND product = 'run'",
                Timestamp.class, model);
        return t == null ? null : t.toInstant();
    }

    /** Já há histórico do ponto/modelo/lead com hora válida em [from, to)? (carga retomável) */
    public boolean hasHistory(long floodPointId, String model, String product, Instant from, Instant to) {
        Integer n = jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT 1 FROM open_meteo_model_hour WHERE flood_point_id = ? AND model = ? AND product = ?
                  AND hour_end_utc >= ? AND hour_end_utc < ? LIMIT 1) x
                """, Integer.class, floodPointId, model, product, utc(from), utc(to));
        return n != null && n > 0;
    }

    public List<Summary> summary() {
        return jdbc.query("""
                SELECT model, product, count(*), min(hour_end_utc), max(hour_end_utc), max(issued_before_utc)
                FROM open_meteo_model_hour GROUP BY model, product ORDER BY model, product
                """, (rs, i) -> new Summary(rs.getString(1), rs.getString(2), rs.getLong(3), rs.getTimestamp(4).toInstant(),
                rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()));
    }

    public Map<String, Object> asMap(Summary s) {
        return Map.of("model", s.model(), "product", s.product(), "rows", s.rows(), "firstHour", s.firstHour(),
                "lastHour", s.lastHour(), "lastIssued", s.lastIssued());
    }
}
