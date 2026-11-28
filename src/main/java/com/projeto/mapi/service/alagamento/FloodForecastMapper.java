package com.projeto.mapi.service.alagamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.dto.AlagamentoPontoDTO;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resposta da IA (snake_case, guardada inteira em flood_forecast_point.payload) para o contrato do front (camelCase).
 * Puro: sem banco nem rede. Motivo desconhecido ou nível fora da lista viram SEM_DADO, nunca SEM_ALERTA.
 */
public final class FloodForecastMapper {

    public static final List<String> LEVELS = List.of("ALERTA", "ATENCAO", "SEM_ALERTA", "SEM_DADO");

    /** Motivos do SEM_DADO com o texto curto da tela. */
    public static final Map<String, String> REASONS = Map.of(
            "CHUVA_AUSENTE", "sem leitura de chuva válida perto do ponto",
            "PREVISAO_INDISPONIVEL", "previsão de chuva (rain_v1) fora do ar",
            "SEM_TERRENO", "sem terreno do ponto",
            "TERRENO_EM_REVISAO", "terreno do ponto em revisão",
            "SERIE_INCOMPLETA", "série de chuva de 10 min com lacuna",
            "MARE_AUSENTE", "sem maré prevista para algum passo (refluxo ligado no nível)");

    public static final String RULE = "ALERTA se a lâmina provável (q50) chega a 15 cm; ATENÇÃO se só a pessimista (q95) chega. "
            + "Experimental.";

    private FloodForecastMapper() {}

    /** Contexto do ponto que a IA não conhece (nome, estação da leitura mais nova, chuva medida da última hora). */
    public record PointContext(long floodPointId, String slug, String name, String rainStation, String rainStationName,
                               Double rainStationKm,
                               Double rainLastHourMm, Instant evaluatedAt, boolean shadow) {}

    /** SEM_DADO montado na própria API (IA fora do ar ou sem resposta). */
    public static AlagamentoPontoDTO unavailable(PointContext c, String reason) {
        String r = REASONS.containsKey(reason) ? reason : "PREVISAO_INDISPONIVEL";
        return new AlagamentoPontoDTO(c.floodPointId(), c.slug(), c.name(), "SEM_DADO", null, r, REASONS.get(r), null,
                c.rainLastHourMm(), List.of(), null, c.rainStation(), c.rainStationName(), c.rainStationKm(), null, Map.of(), List.of(), List.of(), null, null,
                c.evaluatedAt(), true, c.shadow(), RULE, AlagamentoPontoDTO.ATTRIBUTION);
    }

    public static AlagamentoPontoDTO toDto(JsonNode ai, PointContext c) {
        if (ai == null || ai.isNull() || ai.isMissingNode()) return unavailable(c, "PREVISAO_INDISPONIVEL");
        String level = text(ai, "level");
        String reason = text(ai, "reason");
        if (level == null || !LEVELS.contains(level)) {
            level = "SEM_DADO";
            if (reason == null) reason = "PREVISAO_INDISPONIVEL";
        }
        if ("SEM_DADO".equals(level) && (reason == null || !REASONS.containsKey(reason))) reason = "PREVISAO_INDISPONIVEL";
        if (!"SEM_DADO".equals(level)) reason = null;

        List<AlagamentoPontoDTO.Horizon> hs = new ArrayList<>();
        for (JsonNode h : ai.path("horizons")) {
            hs.add(new AlagamentoPontoDTO.Horizon(h.path("hours").asInt(), text(h, "level"), dbl(h, "rain_q50_mm"),
                    dbl(h, "rain_q95_mm"), dbl(h, "sheet_q50_cm"), dbl(h, "sheet_q95_cm"), inst(h, "valid_until"),
                    dbl(h, "sheet_q50_cm_qmin"), dbl(h, "sheet_q95_cm_qmax"), dbl(h, "peak10_q50_mm"), dbl(h, "peak10_q95_mm")));
        }
        Map<String, String> mv = new LinkedHashMap<>();
        JsonNode m = ai.path("model_versions");
        if (m.has("flood")) mv.put("flood", m.path("flood").asText());
        if (m.has("rain")) mv.put("rain", m.path("rain").path("version").asText(null));
        if (m.has("bucket")) mv.put("bucket", m.path("bucket").path("version").asText(null));

        return new AlagamentoPontoDTO(c.floodPointId(), c.slug(), c.name(), level,
                "SEM_DADO".equals(level) ? null : intOrNull(ai, "worst_horizon_hours"), reason,
                reason == null ? null : REASONS.get(reason), dbl(ai, "h_now_cm"), c.rainLastHourMm(), hs,
                explanation(ai.path("explanation")), c.rainStation(), c.rainStationName(), c.rainStationKm(), dbl(ai, "latest_reading_age_minutes"),
                mv, strings(ai.path("sources")), strings(ai.path("missing_inputs")), text(ai, "code_version"), inst(ai, "issued_at"), c.evaluatedAt(), true, c.shadow(), RULE,
                AlagamentoPontoDTO.ATTRIBUTION);
    }

    private static AlagamentoPontoDTO.Explanation explanation(JsonNode e) {
        if (e == null || e.isNull() || e.isMissingNode()) return null;
        List<AlagamentoPontoDTO.ShapItem> shap = new ArrayList<>();
        for (JsonNode s : e.path("shap").path("top")) shap.add(new AlagamentoPontoDTO.ShapItem(text(s, "text"), dbl(s, "contribution_mm")));
        JsonNode cf = e.path("counterfactual");
        JsonNode sv = e.path("sensitivity");
        AlagamentoPontoDTO.Sensitivity sens = sv.isMissingNode() ? null : new AlagamentoPontoDTO.Sensitivity(dbl(sv, "min_cm"),
                dbl(sv, "max_cm"), sv.path("level_changes").asBoolean(false), text(sv, "heaviest_parameter"), text(sv, "text"));
        List<AlagamentoPontoDTO.Step> steps = new ArrayList<>();
        for (JsonNode d : e.path("decomposition")) {
            steps.add(new AlagamentoPontoDTO.Step(inst(d, "t"), d.path("minutes_from_now").asInt(), text(d, "kind"), dbl(d, "rain_mm"),
                    dbl(d, "inflow_m3_h"), dbl(d, "outflow_m3_h"), dbl(d, "volume_m3"), dbl(d, "sheet_cm"), dbl(d, "tide_nr_m"),
                    dbl(d, "outflow_factor")));
        }
        return new AlagamentoPontoDTO.Explanation(text(e, "served_scenario"), intOrNull(e, "served_horizon_hours"), shap,
                dbl(cf, "extra_mm"), text(cf, "text"), sens, steps, dbl(e, "capacity_m3_h"), factors(e.path("factors")),
                capacity(e.path("capacity")), text(e, "served_capacity"), text(e.path("profile"), "method"));
    }

    static AlagamentoPontoDTO.Capacity capacity(JsonNode c) {
        if (c == null || !c.isObject()) return null;
        return new AlagamentoPontoDTO.Capacity(text(c, "base"), dbl(c, "q_min_m3s"), dbl(c, "q_max_m3s"), dbl(c, "i_eq_min_mm_h"),
                dbl(c, "i_eq_max_mm_h"), dbl(c, "kappa_calibrado"));
    }

    /** Fatores do alerta único (ADR 0030); nulo nas respostas de antes. */
    static AlagamentoPontoDTO.Factors factors(JsonNode f) {
        if (f == null || !f.isObject()) return null;
        JsonNode r = f.path("refluxo"), l = f.path("limpeza");
        AlagamentoPontoDTO.Refluxo rf = null;
        if (r.isObject()) {
            List<AlagamentoPontoDTO.TidePeriod> per = new ArrayList<>();
            for (JsonNode x : r.path("periods")) {
                per.add(new AlagamentoPontoDTO.TidePeriod(inst(x, "from"), inst(x, "to"), dbl(x, "mean_reduction_pct"),
                        dbl(x, "max_reduction_pct")));
            }
            JsonNode b = r.path("bias_scenario");
            AlagamentoPontoDTO.BiasScenario bs = b.isObject() ? new AlagamentoPontoDTO.BiasScenario(dbl(b, "mdt_bias_m"),
                    dbl(b, "sill_nr_m"), text(b, "level"), intOrNull(b, "worst_horizon_hours"), grid(b.path("sheet_cm"))) : null;
            rf = new AlagamentoPontoDTO.Refluxo(text(r, "mode"), text(r, "status"), r.path("applied_to_level").asBoolean(false),
                    text(r, "reason"), text(r, "text"), dbl(r, "sill_nr_m"), dbl(r, "tide_now_nr_m"), dbl(r, "tide_max_horizon_nr_m"),
                    dbl(r, "reduction_now_pct"), per, dbl(r, "served_delta_cm"), grid(r.path("delta_sheet_cm")), bs);
        }
        AlagamentoPontoDTO.Limpeza lz = null;
        if (l.isObject()) {
            lz = new AlagamentoPontoDTO.Limpeza(text(l, "mode"), text(l, "status"), l.path("applied_to_level").asBoolean(false),
                    text(l, "reason"), text(l, "text"), text(l, "regime"), dbl(l, "days_since_cleaning"), dbl(l, "p_clogged"),
                    dbl(l, "f_clogged"), text(l, "mixture_level"), grid(l.path("sheet_clogged_cm")), grid(l.path("prob_hcrit")));
        }
        return rf == null && lz == null ? null : new AlagamentoPontoDTO.Factors(rf, lz);
    }

    /** {"q50": {"1": 2.3, ...}, "q95": {...}} -> mapa; vazio se faltar. */
    private static Map<String, Map<String, Double>> grid(JsonNode g) {
        Map<String, Map<String, Double>> out = new LinkedHashMap<>();
        if (g == null || !g.isObject()) return out;
        g.fields().forEachRemaining(e -> {
            Map<String, Double> m = new LinkedHashMap<>();
            e.getValue().fields().forEachRemaining(x -> m.put(x.getKey(), x.getValue().isNumber() ? x.getValue().asDouble() : null));
            out.put(e.getKey(), m);
        });
        return out;
    }

    private static List<String> strings(JsonNode a) {
        List<String> out = new ArrayList<>();
        for (JsonNode x : a) if (x.isTextual()) out.add(x.asText());
        return out;
    }

    private static String text(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isMissingNode() || v.isNull() ? null : v.asText();
    }

    private static Double dbl(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isNumber() ? v.asDouble() : null;
    }

    private static Integer intOrNull(JsonNode n, String f) {
        JsonNode v = n.path(f);
        return v.isNumber() ? v.asInt() : null;
    }

    private static Instant inst(JsonNode n, String f) {
        String s = text(n, f);
        try {
            return s == null ? null : Instant.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
