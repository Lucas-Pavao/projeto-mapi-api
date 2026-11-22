package com.projeto.mapi.service.flood;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Conta pura da conferência semanal do rain_v1 (onda 16, Fase 1.1; regra em docs/PRE-REGISTRO-CONFERENCIA-SEMANAL-RAIN.md).
 * Ganho = 1 − pinball(rain_v1) / pinball(persistência), pinball = média dos três quantis, como no relatório do rain_v1.
 * A persistência é a do treino: faixa da chuva do ponto na última hora → q50, q80, q95 (tabela exportada da IA).
 */
public final class RainForecastCheckCalculator {

    public static final String RULE_VERSION = "conferencia-rain/1";
    private static final ZoneId RECIFE = ZoneId.of("America/Recife");
    private static final double[] A = {0.5, 0.8, 0.95};

    /** Uma previsão conferida. {@code p60} = chuva do ponto na hora antes da emissão; nulo = sem dado. */
    public record Row(Instant issuedAt, int horizonHours, double q50, double q80, double q95, double observedMm, Double p60) {}

    public record Reference(Map<Integer, Map<String, double[]>> persistence, Map<Integer, double[]> testSkill, String origin) {

        public static Reference load(ObjectMapper mapper) {
            try (InputStream in = RainForecastCheckCalculator.class.getResourceAsStream("/rain/referencia_conferencia_semanal.json")) {
                if (in == null) throw new IllegalStateException("referência da conferência semanal ausente");
                JsonNode root = mapper.readTree(in);
                Map<Integer, Map<String, double[]>> pers = new HashMap<>();
                Map<Integer, double[]> skill = new HashMap<>();
                for (int h : new int[]{1, 3, 6}) {
                    Map<String, double[]> bands = new HashMap<>();
                    root.path("persistencia").path(h + "h").fields().forEachRemaining(e -> bands.put(e.getKey(),
                            new double[]{e.getValue().path("q50").asDouble(), e.getValue().path("q80").asDouble(),
                                    e.getValue().path("q95").asDouble()}));
                    pers.put(h, bands);
                    JsonNode t = root.path("teste").path(h + "h");
                    skill.put(h, new double[]{t.path("ganho").asDouble(), t.path("ganho_ic95").get(0).asDouble(),
                            t.path("ganho_ic95").get(1).asDouble()});
                }
                return new Reference(pers, skill, root.path("origem").asText(""));
            } catch (IOException e) {
                throw new IllegalStateException("referência da conferência semanal ilegível", e);
            }
        }
    }

    /** Parâmetros da regra (pré-registro). */
    public record Rule(int minForecasts, int minRainDays, double rainDayMm, int nBoot, long seed) {
        public static Rule defaults() {
            return new Rule(200, 3, 1.0, 2000, 20261122L);
        }
    }

    public record Result(int horizonHours, int nForecasts, int nDays, int nRainDays, Double pinballModel,
                         Double pinballPersistence, Double skill, Double skillLo, Double skillHi, double testSkill,
                         double testSkillLo, double testSkillHi, String status) {}

    private RainForecastCheckCalculator() {}

    /** Faixa do treino (mapi_ai/rain/models.py, key_persist): seco, 0-1, 1-5, >=5; ausente = sem_dado. */
    public static String band(Double p60) {
        if (p60 == null || p60.isNaN()) return "sem_dado";
        if (p60 <= 0.0) return "seco";
        if (p60 <= 1.0) return "0-1";
        if (p60 <= 5.0) return "1-5";
        return ">=5";
    }

    public static double pinball(double y, double q50, double q80, double q95) {
        double[] q = {q50, q80, q95};
        double s = 0;
        for (int i = 0; i < 3; i++) {
            double d = y - q[i];
            s += Math.max(A[i] * d, (A[i] - 1) * d);
        }
        return s / 3.0;
    }

    /** Ganho por prazo, com IC 95% por bootstrap de dia local (dias inteiros reamostrados, semente fixa). */
    public static List<Result> compute(List<Row> rows, Reference ref, Rule rule) {
        Map<Integer, List<Row>> byH = new LinkedHashMap<>();
        for (int h : new int[]{1, 3, 6}) byH.put(h, new ArrayList<>());
        for (Row r : rows) byH.computeIfAbsent(r.horizonHours(), k -> new ArrayList<>()).add(r);
        List<Result> out = new ArrayList<>();
        for (Map.Entry<Integer, List<Row>> e : byH.entrySet()) {
            int h = e.getKey();
            double[] test = ref.testSkill().get(h);
            if (test == null) continue;
            Map<String, double[]> bands = ref.persistence().get(h);
            Map<LocalDate, double[]> day = new LinkedHashMap<>();       // por dia: soma do modelo, soma da persistência
            Map<LocalDate, Boolean> rainy = new HashMap<>();
            for (Row r : e.getValue()) {
                double[] p = bands.getOrDefault(band(r.p60()), bands.get("sem_dado"));
                LocalDate d = r.issuedAt().atZone(RECIFE).toLocalDate();
                double[] acc = day.computeIfAbsent(d, k -> new double[2]);
                acc[0] += pinball(r.observedMm(), r.q50(), r.q80(), r.q95());
                acc[1] += pinball(r.observedMm(), p[0], p[1], p[2]);
                if (r.observedMm() >= rule.rainDayMm()) rainy.put(d, true);
            }
            int n = e.getValue().size();
            int nDays = day.size();
            int nRain = rainy.size();
            double sm = 0, sp = 0;
            for (double[] v : day.values()) {
                sm += v[0];
                sp += v[1];
            }
            if (n == 0) {
                out.add(new Result(h, 0, 0, 0, null, null, null, null, null, test[0], test[1], test[2], "POUCO_DADO"));
                continue;
            }
            Double skill = sp > 0 ? 1 - sm / sp : null;
            double[] ci = skill == null ? null : bootstrap(new ArrayList<>(day.values()), rule);
            String status;
            if (n < rule.minForecasts() || nRain < rule.minRainDays() || skill == null) status = "POUCO_DADO";
            else if (ci[1] < test[1]) status = "PIORA";              // o IC inteiro abaixo do IC do teste
            else status = "OK";
            out.add(new Result(h, n, nDays, nRain, sm / n, sp / n, skill, ci == null ? null : ci[0],
                    ci == null ? null : ci[1], test[0], test[1], test[2], status));
        }
        return out;
    }

    static double[] bootstrap(List<double[]> days, Rule rule) {
        int k = days.size();
        double[] s = new double[rule.nBoot()];
        SplittableRandom rnd = new SplittableRandom(rule.seed());
        for (int b = 0; b < rule.nBoot(); b++) {
            double m = 0, p = 0;
            for (int i = 0; i < k; i++) {
                double[] v = days.get(rnd.nextInt(k));
                m += v[0];
                p += v[1];
            }
            s[b] = p > 0 ? 1 - m / p : Double.NaN;
        }
        double[] ok = java.util.Arrays.stream(s).filter(x -> !Double.isNaN(x)).sorted().toArray();
        if (ok.length == 0) return new double[]{Double.NaN, Double.NaN};
        return new double[]{ok[(int) Math.floor(0.025 * (ok.length - 1))], ok[(int) Math.ceil(0.975 * (ok.length - 1))]};
    }
}
