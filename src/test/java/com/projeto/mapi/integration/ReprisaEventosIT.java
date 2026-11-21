package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.service.alerta.AlertaCycleService;
import com.projeto.mapi.service.alerta.PointRain10MinExportService;
import com.projeto.mapi.service.stream.LiveStreamService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

/**
 * Reprise de eventos reais (Etapa 4.1). Reinjeta as leituras de um período passado no formato da coleta ao vivo e com o
 * atraso da simulação do item 2.3 (projeto-mapi-ai/data/reprise, gerado por mapi_ai.reprise.prepare), num banco de teste,
 * em tempo acelerado (relógio injetado), e deixa o caminho inteiro correr: banco, ciclo do alerta, IA real (rain_v1 e
 * balde), estado, transições e SSE. Roda só com -Dmapi.reprise=true e a IA no ar (AI_API_URL, padrão localhost:8000).
 * Saída: target/reprise/&lt;evento&gt;/sistema.json e serie10min_&lt;ponto&gt;.csv, que o check.py da IA confere contra o balde fora.
 */
@TestPropertySource(properties = {"mapi.rain-forecast.enabled=true", "mapi.rain-forecast.open-meteo-enabled=false",
        "app.ai.api-url=${AI_API_URL:http://localhost:8000}",
        // a reprise confere o balde congelado (ADR 0027): sem refluxo nem limpeza, que vieram depois (onda 14)
        "mapi.alerta.refluxo.enabled=false", "mapi.limpeza.enabled=false"})
class ReprisaEventosIT extends AbstractTimescaleIntegrationTest {

    static final Path DATA = Path.of(System.getProperty("mapi.reprise.dir", "../projeto-mapi-ai/data/reprise"));
    static final Path OUT = Path.of("target/reprise");
    static final DateTimeFormatter TS = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2020-01-01T00:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @TestConfiguration
    static class ClockCfg {
        @Bean
        @Primary
        MutableClock reprisaClock() {
            return new MutableClock();
        }
    }

    @Autowired MutableClock clock;
    @Autowired AlertaCycleService cycle;
    @Autowired PointRain10MinExportService export10;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean LiveStreamService stream;
    @MockitoSpyBean com.projeto.mapi.service.alerta.PointBucketClient bucket;
    final List<Map<String, Object>> bucketCalls = new ArrayList<>();
    final ObjectMapper mapper = new ObjectMapper();

    record Reading(String station, Instant ts, double mm, double delayMin) {
        Instant arrives() {
            return ts.plusMillis(Math.round(delayMin * 60_000));
        }
    }

    static Instant inst(Object o) {
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime t) return t.toInstant();
        return Instant.parse(o.toString());
    }

    static boolean aiUp() {
        try {
            String base = System.getenv().getOrDefault("AI_API_URL", "http://localhost:8000");
            HttpURLConnection c = (HttpURLConnection) URI.create(base + "/health").toURL().openConnection();
            c.setConnectTimeout(2000);
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    void loadBase() throws Exception {
        limparDependentesDePontos();
        jdbc.update("DELETE FROM flood_points");
        jdbc.update("DELETE FROM station_month_check");
        jdbc.update("DELETE FROM station_alias");
        jdbc.update("DELETE FROM station_canonical");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(new GZIPInputStream(
                getClass().getResourceAsStream("/reprise/base.sql.gz")), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.startsWith("INSERT") || line.startsWith("SELECT pg_catalog.setval")) jdbc.execute(line);
            }
        }
        // a coleta ao vivo usa o mesmo código da PED: apelido CEMADEN de cada estação canônica
        jdbc.update("""
                INSERT INTO station (source, station_id, name, lat, lon)
                SELECT 'CEMADEN', canonical_id, name, lat, lon FROM station_canonical WHERE primary_source = 'CEMADEN_PED'
                ON CONFLICT (source, station_id) DO NOTHING""");
        jdbc.update("""
                INSERT INTO station_alias (source, station_id, canonical_id, distance_m, rule_version)
                SELECT 'CEMADEN', canonical_id, canonical_id, 0, 'reprise' FROM station_canonical WHERE primary_source = 'CEMADEN_PED'
                ON CONFLICT DO NOTHING""");
    }

    void clearEvent() {
        for (String t : List.of("alert_transition", "alert_point_cycle", "alert_point_state", "live_qc_drop", "rain_forecast"))
            jdbc.update("DELETE FROM " + t);
        jdbc.update("DELETE FROM alert_cycle");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
    }

    List<Reading> readings(Path csv) throws Exception {
        List<Reading> out = new ArrayList<>();
        List<String> lines = Files.readAllLines(csv);
        for (String l : lines.subList(1, lines.size())) {
            String[] c = l.split(",");
            out.add(new Reading(c[0], Instant.from(TS.parse(c[1].replace(" ", "T"))), Double.parseDouble(c[2]), Double.parseDouble(c[3])));
        }
        return out;
    }

    void insert(UUID run, Reading r, long seq) {
        String hash = String.format("%064x", new java.math.BigInteger(1, (r.station() + r.ts()).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 64);
        Timestamp arr = Timestamp.from(r.arrives());
        jdbc.update("""
                INSERT INTO raw_observation (id, ingested_at, run_id, source, station_id, collected_at, source_ts_text,
                    source_ts_norm, source_tz, payload, content_hash)
                VALUES (gen_random_uuid(), ?, ?, 'CEMADEN', ?, ?, ?, ?, 'UTC', '{}'::jsonb, ?)""",
                arr, run, r.station(), arr, r.ts().toString(), r.ts().toString(), hash);
        jdbc.update("""
                INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc,
                    status, run_id, raw_hash, rule_version, value_hash, origin_ts_norm)
                VALUES ('CEMADEN', ?, 'precipitation', 'EVENT', ?, ?, 'mm', ?, 'ACTIVE', ?, ?, 'reprise', ?, ?)""",
                r.station(), Timestamp.from(r.ts()), r.mm(), Timestamp.from(r.ts()), run, hash, hash, r.ts().toString());
    }

    @Test
    void reprisaDosCincoEventos() throws Exception {
        Assumptions.assumeTrue(Boolean.getBoolean("mapi.reprise"), "reprise só com -Dmapi.reprise=true");
        Assumptions.assumeTrue(Files.isDirectory(DATA), "sem " + DATA + " (rode python -m mapi_ai.reprise.prepare)");
        Assumptions.assumeTrue(aiUp(), "IA fora do ar");
        loadBase();
        org.mockito.Mockito.doAnswer(inv -> {
            Object r = inv.callRealMethod();
            com.projeto.mapi.service.alerta.PointBucketClient.Terrain t = inv.getArgument(1);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("at", clock.now.toString());
            m.put("slug", t.slug());
            m.put("rain", inv.getArgument(0));
            m.put("terrain", t);
            m.put("tr", inv.getArgument(2));
            m.put("te", inv.getArgument(3));
            m.put("q50", inv.getArgument(4));
            m.put("q80", inv.getArgument(5));
            m.put("result", r);
            bucketCalls.add(m);
            return r;
        }).when(bucket).evaluate(any(), any(), org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble(), any(), any());
        List<String> failures = new ArrayList<>();
        JsonNode events = mapper.readTree(DATA.resolve("events.json").toFile());
        for (JsonNode ev : events) {
            String id = ev.get("id").asText();
            JsonNode exp = mapper.readTree(DATA.resolve(id).resolve("expected.json").toFile());
            clearEvent();
            UUID run = ObservationFixtures.run(jdbc, "CEMADEN");
            List<Reading> rs = readings(DATA.resolve(id).resolve("readings.csv"));
            rs.sort((a, b) -> a.arrives().compareTo(b.arrives()));
            List<Instant> cycles = new ArrayList<>();
            for (JsonNode c : exp.get("cycles")) cycles.add(Instant.from(TS.parse(c.asText().replace(" ", "T"))));
            Instant start = cycles.get(0);
            int k = 0;
            long seq = 0;
            while (k < rs.size() && !rs.get(k).arrives().isAfter(start)) insert(run, rs.get(k++), seq++);
            clearInvocations(stream);
            bucketCalls.clear();
            Map<String, List<Map<String, Object>>> timeline = new LinkedHashMap<>();
            long t0 = System.nanoTime();
            for (Instant c : cycles) {
                while (k < rs.size() && !rs.get(k).arrives().isAfter(c)) insert(run, rs.get(k++), seq++);
                clock.now = c;
                cycle.run("REPRISE");
            }
            // cauda: depois da janela, segue recebendo leituras até o alerta desligar ou expirar (no máximo 6 h)
            Instant tail = cycles.get(cycles.size() - 1);
            int tailCycles = 0;
            while (tailCycles < 120 && jdbc.queryForObject(
                    "SELECT count(*) FROM alert_point_state WHERE rain_alert IN ('ATENCAO', 'ALERTA')", Integer.class) > 0) {
                tail = tail.plus(Duration.ofMinutes(3));
                while (k < rs.size() && !rs.get(k).arrives().isAfter(tail)) insert(run, rs.get(k++), seq++);
                clock.now = tail;
                cycle.run("REPRISE");
                tailCycles++;
            }
            double secs = (System.nanoTime() - t0) / 1e9;
            for (Map<String, Object> row : jdbc.queryForList("""
                    SELECT f.slug, c.evaluated_at, c.rain_alert, c.rain_reason, c.point_flood, c.flood_reason, c.prob_forte_1h,
                           c.rain_te_mm, c.h_now_mm, c.h_proj_q50_max_mm, c.flood_in_minutes, c.reading_ts, c.event_id::text AS event_id
                    FROM alert_point_cycle c JOIN flood_points f ON f.id = c.flood_point_id ORDER BY f.slug, c.evaluated_at""")) {
                String slug = (String) row.get("slug");
                Map<String, Object> m = new LinkedHashMap<>(row);
                m.remove("slug");
                m.put("evaluated_at", inst(row.get("evaluated_at")).toString());
                m.put("reading_ts", row.get("reading_ts") == null ? null : inst(row.get("reading_ts")).toString());
                timeline.computeIfAbsent(slug, x -> new ArrayList<>()).add(m);
            }
            // 1. ligou no instante esperado pela simulação (tolerância de 1 ciclo)
            Map<String, Object> firstOn = new LinkedHashMap<>();
            for (var e : exp.get("points").properties()) {
                String slug = e.getKey();
                JsonNode fo = e.getValue().get("first_on");
                Instant expected = fo == null || fo.isNull() ? null : Instant.from(TS.parse(fo.asText().replace(" ", "T")));
                Instant got = timeline.getOrDefault(slug, List.of()).stream()
                        .filter(m -> "ATENCAO".equals(m.get("rain_alert")) || "ALERTA".equals(m.get("rain_alert")))
                        .map(m -> Instant.parse((String) m.get("evaluated_at")))
                        .findFirst().orElse(null);
                firstOn.put(slug, Map.of("esperado", String.valueOf(expected), "sistema", String.valueOf(got)));
                boolean ok = expected == null ? got == null
                        : got != null && Duration.between(expected, got).abs().compareTo(Duration.ofMinutes(3)) <= 0;
                if (!ok) failures.add(id + " " + slug + ": ligou em " + got + ", esperado " + expected);
            }
            // 2. as transições chegaram pelo SSE
            long sse = mockingDetails(stream).getInvocations().stream()
                    .filter(i -> i.getMethod().getName().equals("publishAlertEvent") && "alert-transition".equals(i.getArgument(0))).count();
            Integer dbTr = jdbc.queryForObject("SELECT count(*) FROM alert_transition", Integer.class);
            if (sse != dbTr) failures.add(id + ": " + sse + " transições no SSE, " + dbTr + " no banco");
            // 3. quem ligou foi cancelado, expirou ou desligou no fim
            for (Map<String, Object> last : jdbc.queryForList("""
                    SELECT DISTINCT ON (t.flood_point_id) f.slug, t.to_state, t.kind FROM alert_transition t
                    JOIN flood_points f ON f.id = t.flood_point_id WHERE t.field = 'RAIN'
                    ORDER BY t.flood_point_id, t.at DESC, t.id DESC""")) {
                String to = (String) last.get("to_state");
                if ("ATENCAO".equals(to) || "ALERTA".equals(to))
                    failures.add(id + " " + last.get("slug") + ": terminou ligado (" + to + ")");
            }
            // 4. nunca NENHUM nem NAO com dado faltando (leitura ausente ou com mais de 15 min)
            Integer bad = jdbc.queryForObject("""
                    SELECT count(*) FROM alert_point_cycle WHERE (reading_ts IS NULL OR evaluated_at - reading_ts > interval '15 minutes')
                      AND (rain_alert = 'NENHUM' OR point_flood = 'NAO')""", Integer.class);
            if (bad != null && bad > 0) failures.add(id + ": " + bad + " ciclo(s) com NENHUM ou NAO e dado faltando");
            // saída para o check.py (balde fora do sistema) e para o extrato
            Path dir = OUT.resolve(id);
            Files.createDirectories(dir);
            Map<String, Object> sys = new LinkedHashMap<>();
            sys.put("event", id);
            sys.put("cycles", cycles.size());
            sys.put("tail_cycles", tailCycles);
            sys.put("seconds", secs);
            sys.put("first_on", firstOn);
            sys.put("sse_transitions", sse);
            sys.put("db_transitions", dbTr);
            sys.put("transitions", jdbc.queryForList("""
                    SELECT f.slug, t.at::text, t.field, t.from_state, t.to_state, t.kind, t.reason, t.event_id::text
                    FROM alert_transition t JOIN flood_points f ON f.id = t.flood_point_id ORDER BY t.at, t.id"""));
            sys.put("timeline", timeline);
            Files.writeString(dir.resolve("sistema.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(sys));
            Files.writeString(dir.resolve("balde_chamadas.json"), mapper.writeValueAsString(bucketCalls));
            for (String slug : timeline.keySet()) {
                StringWriter w = new StringWriter();
                export10.writeCsv(slug, cycles.get(0).minus(Duration.ofHours(24)), cycles.get(cycles.size() - 1), w);
                Files.writeString(dir.resolve("serie10min_" + slug + ".csv"), w.toString());
            }
        }
        Files.writeString(OUT.resolve("falhas.txt"), String.join("\n", failures));
        assertThat(failures).as("reprise").isEmpty();
    }
}
