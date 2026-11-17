package com.projeto.mapi.service.alerta.impl;

import com.projeto.mapi.repository.analise.PointRain10MinRepository.Block;
import com.projeto.mapi.service.alerta.LiveQualityControl;
import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import com.projeto.mapi.service.analise.StationMonthChecker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Controle de qualidade ao vivo (item 2.4, emenda ao ADR 0022). Estação suspeita: os critérios da checagem mensal
 * ({@link StationMonthChecker}, mesmos limiares) numa janela móvel de 30 dias terminando em t, sobre a série horária
 * canônica. Estação reprovada (FAIL) sai da série do alerta com o motivo (QC_STUCK_ZERO, QC_UNDERCATCH, ...),
 * registrado em live_qc_drop. O pico isolado já sai no compositor da série de 10 min (mesma regra do ADR 0022).
 * Recalcula no máximo uma vez por hora do relógio do ciclo (a reprise usa o tempo simulado).
 */
@Service
@Slf4j
public class LiveQualityControlImpl implements LiveQualityControl {

    static final Duration WINDOW = Duration.ofDays(30);
    static final Duration REFRESH = Duration.ofHours(1);

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private volatile Map<String, String> failReason = Map.of();
    private volatile Instant asOf;

    public LiveQualityControlImpl(JdbcTemplate jdbc, @Value("${mapi.alerta.live-qc.enabled:true}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    @Override
    public PointRain10MinComposer.LiveFilter filterFor(long floodPointId, List<Block> blocks, Instant now) {
        if (!enabled) return PointRain10MinComposer.LiveFilter.NONE;
        refreshIfNeeded(now);
        Map<String, String> fails = failReason;
        if (fails.isEmpty()) return PointRain10MinComposer.LiveFilter.NONE;
        return b -> fails.get(b.canonicalId());
    }

    /** Estações reprovadas na janela de 30 dias até {@code now}, com o motivo (para a rota e o teste). */
    public Map<String, String> failures(Instant now) {
        refreshIfNeeded(now);
        return failReason;
    }

    private synchronized void refreshIfNeeded(Instant now) {
        if (asOf != null && !now.isBefore(asOf) && Duration.between(asOf, now).compareTo(REFRESH) < 0) return;
        try {
            failReason = compute(now);
            asOf = now;
        } catch (Exception e) {
            log.warn("Controle de qualidade ao vivo indisponível: {}", e.toString());
        }
    }

    Map<String, String> compute(Instant now) {
        Instant end = now.truncatedTo(ChronoUnit.HOURS);
        Instant start = end.minus(WINDOW);
        int n = (int) Duration.between(start, end).toHours();
        Map<String, double[]> pos = new LinkedHashMap<>();
        jdbc.query("SELECT canonical_id, lat, lon FROM station_canonical WHERE lat IS NOT NULL AND lon IS NOT NULL",
                rs -> {
                    pos.put(rs.getString(1), new double[]{rs.getDouble(2), rs.getDouble(3)});
                });
        Map<String, double[]> mm = new HashMap<>();
        Map<String, double[]> cov = new HashMap<>();
        for (String id : pos.keySet()) {
            double[] a = new double[n], c = new double[n];
            java.util.Arrays.fill(a, Double.NaN);
            java.util.Arrays.fill(c, Double.NaN);
            mm.put(id, a);
            cov.put(id, c);
        }
        jdbc.query("""
                SELECT canonical_id, hour_end_utc, value, blocks_covered_min, source FROM canonical_rain_hourly_v
                WHERE hour_end_utc > ? AND hour_end_utc <= ?
                """, rs -> {
            double[] a = mm.get(rs.getString(1));
            if (a == null) return;
            Timestamp ts = rs.getTimestamp(2);
            int i = (int) Duration.between(start, ts.toInstant()).toHours() - 1;
            if (i < 0 || i >= n) return;
            a[i] = rs.getDouble(3);
            Object c = rs.getObject(4);
            // A coleta ao vivo grava interval_start = ts (bloco sem duração): a cobertura da hora é desconhecida e não
            // pode reprovar a estação por cobertura parcial. Conta como hora coberta; os outros critérios continuam.
            boolean live = "CEMADEN".equals(rs.getString(5));
            cov.get(rs.getString(1))[i] = live ? 60.0 : c == null ? Double.NaN : ((Number) c).doubleValue();
        }, OffsetDateTime.ofInstant(start, ZoneOffset.UTC), OffsetDateTime.ofInstant(end, ZoneOffset.UTC));
        List<StationMonthChecker.StationMonth> sm = new ArrayList<>();
        for (Map.Entry<String, double[]> e : pos.entrySet()) {
            sm.add(new StationMonthChecker.StationMonth(e.getKey(), e.getValue()[0], e.getValue()[1], mm.get(e.getKey()),
                    cov.get(e.getKey())));
        }
        Map<String, String> out = new HashMap<>();
        for (StationMonthChecker.Result r : StationMonthChecker.check(sm, StationMonthChecker.Rules.defaults())) {
            if ("FAIL".equals(r.status())) out.put(r.stationId(), "QC_" + String.join("_", r.reasons()));
        }
        log.info("Controle de qualidade ao vivo (30 dias até {}): {} estação(ões) reprovada(s) {}", end, out.size(), out);
        return out;
    }
}
