package com.projeto.mapi.repository.analise;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator.Cycle;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ciclos do alerta único (V38) e gravação do relatório semanal do modo sombra (V49). */
@Repository
@RequiredArgsConstructor
public class ShadowWeeklyReportRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    /** Pontos ativos: id -> slug. */
    public Map<Long, String> activePoints() {
        Map<Long, String> out = new LinkedHashMap<>();
        jdbc.query("SELECT id, slug FROM flood_points WHERE active IS TRUE ORDER BY id", rs -> {
            out.put(rs.getLong(1), rs.getString(2));
        });
        return out;
    }

    public List<Cycle> cycles(Instant from, Instant to) {
        return jdbc.query("""
                SELECT flood_point_id, evaluated_at, level, reason FROM flood_forecast_point
                WHERE evaluated_at >= ? AND evaluated_at < ?
                """, (rs, i) -> new Cycle(rs.getLong(1), rs.getTimestamp(2).toInstant(), rs.getString(3), rs.getString(4)),
                utc(from), utc(to));
    }

    public void upsert(Instant from, Instant to, Result r, String ruleVersion) {
        jdbc.update("""
                INSERT INTO shadow_weekly_report (week_from, week_to, flood_point_id, slug, n_cycles, n_expected, longest_gap_min,
                    gaps_over_30min, by_level, sem_dado_by_reason, alerta_episodes, alerta_ou_atencao_episodes, rule_version,
                    computed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, now())
                ON CONFLICT (week_from, flood_point_id, rule_version) DO UPDATE SET
                    week_to = EXCLUDED.week_to, slug = EXCLUDED.slug, n_cycles = EXCLUDED.n_cycles,
                    n_expected = EXCLUDED.n_expected, longest_gap_min = EXCLUDED.longest_gap_min,
                    gaps_over_30min = EXCLUDED.gaps_over_30min, by_level = EXCLUDED.by_level,
                    sem_dado_by_reason = EXCLUDED.sem_dado_by_reason, alerta_episodes = EXCLUDED.alerta_episodes,
                    alerta_ou_atencao_episodes = EXCLUDED.alerta_ou_atencao_episodes, computed_at = now()
                """, utc(from), utc(to), r.pointId(), r.slug(), r.nCycles(), r.nExpected(), r.longestGapMin(), r.gapsOver30Min(),
                json(r.byLevel()), json(r.semDadoByReason()), r.alertaEpisodes(), r.alertaOuAtencaoEpisodes(), ruleVersion);
    }

    /** Últimas semanas gravadas, da mais nova para a mais velha. */
    public List<Map<String, Object>> latestWeeks(int weeks) {
        return jdbc.queryForList("""
                SELECT week_from, week_to, slug, n_cycles, n_expected, longest_gap_min, gaps_over_30min, by_level::text AS by_level,
                       sem_dado_by_reason::text AS sem_dado_by_reason, alerta_episodes, alerta_ou_atencao_episodes, labels_status,
                       rule_version, computed_at
                FROM shadow_weekly_report
                WHERE week_from IN (SELECT DISTINCT week_from FROM shadow_weekly_report ORDER BY week_from DESC LIMIT ?)
                ORDER BY week_from DESC, slug
                """, weeks);
    }

    private String json(Map<String, Integer> m) {
        try {
            return mapper.writeValueAsString(m);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
