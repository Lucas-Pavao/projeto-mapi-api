package com.projeto.mapi.service.alerta.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AlertaProperties;
import com.projeto.mapi.dto.AlertaConferenciaDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.alerta.AlertaRepository;
import com.projeto.mapi.repository.analise.PointRain10MinRepository;
import com.projeto.mapi.service.alerta.AlertaConferenciaService;
import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Conferência diária dos alertas da véspera (item 4.2), junto da conferência da previsão de chuva (03:30 UTC).
 * Evento FORTE = cruzamento de 25,15 mm em 60 min na série de 10 min do ponto (mesma regra do alerta), sem outro nas
 * 6 h anteriores. Avisado = rain_alert ligado em algum ciclo nos 60 min antes do cruzamento. Episódio falso = ligado sem
 * cruzamento até 60 min depois do fim. ALAGAMENTO_PROVAVEL não tem verdade de campo: os episódios ficam guardados.
 * Também gera, sozinho, o relatório de atraso do primeiro dia com chuva acima de 10 mm em algum ponto (item 2.1).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AlertaConferenciaServiceImpl implements AlertaConferenciaService {

    static final double FORTE_MM_60 = 25.15;
    static final double WET_DAY_MM = 10.0;

    private final FloodPointRepository points;
    private final PointRain10MinRepository rainRepo;
    private final AlertaRepository alertaRepo;
    private final AlertaProperties props;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Scheduled(cron = "${mapi.alerta.check-cron:0 35 3 * * *}", zone = "UTC")
    public void scheduled() {
        try {
            LocalDate yesterday = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC).minusDays(1);
            int n = checkDay(yesterday);
            int r = latencyReports();
            log.info("Conferência do alerta de {}: {} ponto(s); {} relatório(s) de atraso novo(s)", yesterday, n, r);
        } catch (Exception e) {
            log.error("Falha na conferência diária do alerta: {}", e.toString(), e);
        }
    }

    record Cycle(Instant at, String rain, String flood, Double hNow, Double hProj) {}

    private static OffsetDateTime utc(Instant i) {
        return OffsetDateTime.ofInstant(i, ZoneOffset.UTC);
    }

    @Override
    public int checkDay(LocalDate dayUtc) {
        Instant start = dayUtc.atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant end = start.plus(Duration.ofDays(1));
        int done = 0;
        for (FloodPoint p : points.findByActiveTrue()) {
            Instant from = start.minus(Duration.ofHours(7));
            List<PointRain10MinRepository.Block> blocks = rainRepo.blocks(p.getId(), from, end, 11.0, false);
            Map<String, String> month = rainRepo.monthStatus(blocks.stream().map(PointRain10MinRepository.Block::canonicalId)
                    .distinct().toList(), from, end);
            List<PointRain10MinComposer.Slot> slots = PointRain10MinComposer.compose(blocks, month, from, end,
                    PointRain10MinComposer.LiveFilter.NONE).slots();
            List<Instant> crossings = crossings(slots, start);
            List<Cycle> cycles = jdbc.query("""
                    SELECT evaluated_at, rain_alert, point_flood, h_now_mm, h_proj_q50_max_mm FROM alert_point_cycle
                    WHERE flood_point_id = ? AND evaluated_at >= ? AND evaluated_at < ? ORDER BY evaluated_at
                    """, (rs, i) -> new Cycle(rs.getObject(1, OffsetDateTime.class).toInstant(), rs.getString(2), rs.getString(3),
                    rs.getObject(4, Double.class), rs.getObject(5, Double.class)), p.getId(), utc(start.minus(Duration.ofHours(2))), utc(end.plus(Duration.ofHours(1))));
            Row r = evaluate(crossings, cycles, start, end);
            jdbc.update("""
                    INSERT INTO alert_daily_check (day_utc, flood_point_id, forte_events, forte_warned, lead_minutes, alert_episodes,
                        false_episodes, sem_dado_minutes, evaluated_minutes, flood_episodes, shadow, checked_at)
                    VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?, ?, ?::jsonb, ?, ?)
                    ON CONFLICT (day_utc, flood_point_id) DO UPDATE SET forte_events = EXCLUDED.forte_events,
                        forte_warned = EXCLUDED.forte_warned, lead_minutes = EXCLUDED.lead_minutes,
                        alert_episodes = EXCLUDED.alert_episodes, false_episodes = EXCLUDED.false_episodes,
                        sem_dado_minutes = EXCLUDED.sem_dado_minutes, evaluated_minutes = EXCLUDED.evaluated_minutes,
                        flood_episodes = EXCLUDED.flood_episodes, shadow = EXCLUDED.shadow, checked_at = EXCLUDED.checked_at
                    """, Date.valueOf(dayUtc), p.getId(), r.events, r.warned, json(r.leads), r.episodes, r.falseEp, r.semDado,
                    r.evaluated, json(r.floodEpisodes), props.isShadow(), utc(clock.instant()));
            done++;
        }
        return done;
    }

    /** Cruzamentos de 25,15 mm em 60 min com fim no dia, sem outro nas 6 h anteriores. Passo nulo conta como sem chuva. */
    static List<Instant> crossings(List<PointRain10MinComposer.Slot> slots, Instant dayStart) {
        List<Instant> out = new ArrayList<>();
        int last = -1000;
        for (int i = 5; i < slots.size(); i++) {
            double s = 0;
            for (int k = i - 5; k <= i; k++) s += slots.get(k).mm() == null ? 0 : slots.get(k).mm();
            if (s >= FORTE_MM_60) {
                if (i - last >= 36 && !slots.get(i).end().isBefore(dayStart)) out.add(slots.get(i).end());
                last = i;
            }
        }
        return out;
    }

    static final class Row {
        int events, warned, episodes, falseEp;
        double semDado, evaluated;
        List<Double> leads = new ArrayList<>();
        List<Map<String, Object>> floodEpisodes = new ArrayList<>();
    }

    static boolean on(String rain) {
        return "ATENCAO".equals(rain) || "ALERTA".equals(rain);
    }

    static Row evaluate(List<Instant> crossings, List<Cycle> cycles, Instant start, Instant end) {
        Row r = new Row();
        r.events = crossings.size();
        for (Instant c : crossings) {
            Double lead = null;
            for (Cycle cy : cycles) {
                if (cy.at().isBefore(c.minus(Duration.ofMinutes(60))) || !cy.at().isBefore(c)) continue;
                if (on(cy.rain())) {
                    lead = Duration.between(cy.at(), c).toSeconds() / 60.0;
                    break;
                }
            }
            if (lead != null) r.warned++;
            r.leads.add(lead == null ? 0.0 : lead);
        }
        Instant epStart = null, epEnd = null, fStart = null;
        double fMax = 0;
        for (int i = 0; i < cycles.size(); i++) {
            Cycle cy = cycles.get(i);
            boolean inDay = !cy.at().isBefore(start) && cy.at().isBefore(end);
            Instant next = i + 1 < cycles.size() ? cycles.get(i + 1).at() : cy.at().plus(Duration.ofMinutes(3));
            double dt = Math.min(10.0, Duration.between(cy.at(), next).toSeconds() / 60.0);
            if (inDay) {
                r.evaluated += dt;
                if ("SEM_DADO".equals(cy.rain())) r.semDado += dt;
            }
            boolean gap = epEnd != null && Duration.between(epEnd, cy.at()).toMinutes() > 10;
            if (on(cy.rain()) && !gap) {
                if (epStart == null) epStart = cy.at();
                epEnd = cy.at();
            } else {
                if (epStart != null) closeEpisode(r, epStart, epEnd, crossings, start, end);
                epStart = on(cy.rain()) ? cy.at() : null;
                epEnd = epStart;
            }
            if ("PROVAVEL".equals(cy.flood())) {
                if (fStart == null) fStart = cy.at();
                fMax = Math.max(fMax, Math.max(cy.hNow() == null ? 0 : cy.hNow(), cy.hProj() == null ? 0 : cy.hProj()));
            } else if (fStart != null) {
                closeFlood(r, fStart, cy.at(), fMax, start, end);
                fStart = null;
                fMax = 0;
            }
        }
        if (epStart != null) closeEpisode(r, epStart, epEnd, crossings, start, end);
        if (fStart != null) closeFlood(r, fStart, cycles.get(cycles.size() - 1).at(), fMax, start, end);
        return r;
    }

    private static void closeEpisode(Row r, Instant s, Instant e, List<Instant> crossings, Instant start, Instant end) {
        if (s.isBefore(start) || !s.isBefore(end)) return;
        r.episodes++;
        boolean hit = crossings.stream().anyMatch(c -> !c.isBefore(s) && !c.isAfter(e.plus(Duration.ofMinutes(60))));
        if (!hit) r.falseEp++;
    }

    private static void closeFlood(Row r, Instant s, Instant e, double hMax, Instant start, Instant end) {
        if (s.isBefore(start) || !s.isBefore(end)) return;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("inicio", s.toString());
        m.put("fim", e.toString());
        m.put("laminaMaxCm", Math.round(hMax) / 10.0);
        r.floodEpisodes.add(m);
    }

    private String json(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            return "null";
        }
    }

    @Override
    public int latencyReports() {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        int made = 0;
        for (int back = 1; back <= 7; back++) {
            LocalDate day = today.minusDays(back);
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM alert_latency_report WHERE day_utc = ?", Integer.class,
                    Date.valueOf(day));
            if (exists != null && exists > 0) continue;
            Instant s = day.atStartOfDay().toInstant(ZoneOffset.UTC), e = s.plus(Duration.ofDays(1));
            double maxMm = 0;
            for (FloodPoint p : points.findByActiveTrue()) {
                List<PointRain10MinRepository.Block> blocks = rainRepo.blocks(p.getId(), s, e, 5.0, false);
                double sum = PointRain10MinComposer.compose(blocks, Map.of(), s, e, PointRain10MinComposer.LiveFilter.NONE)
                        .slots().stream().mapToDouble(x -> x.mm() == null ? 0 : x.mm()).sum();
                maxMm = Math.max(maxMm, sum);
            }
            if (maxMm < WET_DAY_MM) continue;
            List<AlertaRepository.LatencyStat> stats = alertaRepo.latency(s, e);
            double budget = props.getLatency().getBudgetP90Minutes() * 60.0;
            Boolean within = stats.stream().filter(x -> x.segment().equals("total") && x.wetHour()).findFirst()
                    .map(x -> x.p90S() != null && x.p90S() < budget).orElse(null);
            jdbc.update("INSERT INTO alert_latency_report (day_utc, max_point_rain_mm, report, within_budget_wet, generated_at) "
                    + "VALUES (?, ?, ?::jsonb, ?, ?) ON CONFLICT (day_utc) DO NOTHING", Date.valueOf(day), maxMm, json(stats), within,
                    utc(clock.instant()));
            log.warn("Relatório de atraso gerado para {} (chuva máxima no ponto {} mm): dentro do orçamento em hora com chuva = {}",
                    day, Math.round(maxMm * 10) / 10.0, within);
            made++;
        }
        return made;
    }

    @Override
    public AlertaConferenciaDTO summary(int days) {
        LocalDate to = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        List<AlertaConferenciaDTO.Row> rows = jdbc.query("""
                SELECT c.*, f.slug FROM alert_daily_check c JOIN flood_points f ON f.id = c.flood_point_id
                WHERE c.day_utc > ? ORDER BY c.day_utc DESC, f.id
                """, (rs, i) -> new AlertaConferenciaDTO.Row(rs.getDate("day_utc").toLocalDate(), rs.getString("slug"),
                rs.getInt("forte_events"), rs.getInt("forte_warned"), read(rs.getString("lead_minutes"), new TypeReference<List<Double>>() {}),
                rs.getInt("alert_episodes"), rs.getInt("false_episodes"), rs.getDouble("sem_dado_minutes"),
                rs.getDouble("evaluated_minutes"), read(rs.getString("flood_episodes"), new TypeReference<List<Map<String, Object>>>() {}),
                rs.getBoolean("shadow")), Date.valueOf(to.minusDays(days)));
        int ev = rows.stream().mapToInt(AlertaConferenciaDTO.Row::forteEvents).sum();
        int warned = rows.stream().mapToInt(AlertaConferenciaDTO.Row::forteWarned).sum();
        int falses = rows.stream().mapToInt(AlertaConferenciaDTO.Row::falseEpisodes).sum();
        double sem = rows.stream().mapToDouble(AlertaConferenciaDTO.Row::semDadoMinutes).sum();
        double tot = rows.stream().mapToDouble(AlertaConferenciaDTO.Row::evaluatedMinutes).sum();
        int floods = rows.stream().mapToInt(r -> r.floodEpisodes() == null ? 0 : r.floodEpisodes().size()).sum();
        List<Double> leads = rows.stream().flatMap(r -> r.leadMinutes() == null ? java.util.stream.Stream.<Double>empty() : r.leadMinutes().stream())
                .sorted().toList();
        Double med = leads.isEmpty() ? null : (leads.size() % 2 == 1 ? leads.get(leads.size() / 2)
                : (leads.get(leads.size() / 2 - 1) + leads.get(leads.size() / 2)) / 2);
        String note = ev == 0 ? "Nenhum evento FORTE medido no período: antecedência e falsos por evento ainda sem medida." : null;
        return new AlertaConferenciaDTO(days, ev, warned, med, ev == 0 ? falses : (double) falses / ev, tot == 0 ? 0 : sem / tot,
                floods, rows, note);
    }

    private <T> T read(String s, TypeReference<T> t) {
        try {
            return s == null ? null : mapper.readValue(s, t);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Map<String, Object>> reports() {
        return jdbc.query("SELECT day_utc, max_point_rain_mm, report::text, within_budget_wet, generated_at FROM alert_latency_report "
                + "ORDER BY day_utc DESC LIMIT 60", (rs, i) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("day", rs.getDate(1).toLocalDate().toString());
            m.put("maxPointRainMm", rs.getDouble(2));
            m.put("segments", read(rs.getString(3), new TypeReference<List<Map<String, Object>>>() {}));
            m.put("withinBudgetWet", rs.getObject(4));
            m.put("generatedAt", rs.getObject(5, OffsetDateTime.class).toInstant().toString());
            return m;
        });
    }
}
