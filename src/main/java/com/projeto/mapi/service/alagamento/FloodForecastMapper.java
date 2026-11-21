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
            "SERIE_INCOMPLETA", "série de chuva de 10 min com lacuna");

    public static final String RULE = "ALERTA se a lâmina provável (q50) chega a 15 cm; ATENÇÃO se só a pessimista (q95) chega. "
            + "Experimental.";

    private FloodForecastMapper() {}

    /** Contexto do ponto que a IA não conhece (nome, estação da leitura mais nova, chuva medida da última hora). */
    public record PointContext(long floodPointId, String slug, String name, String rainStation, Double rainStationKm,
                               Double rainLastHourMm, Instant evaluatedAt, boolean shadow) {}

    /** SEM_DADO montado na própria API (IA fora do ar ou sem resposta). */
    public static AlagamentoPontoDTO unavailable(PointContext c, String reason) {
        String r = REASONS.containsKey(reason) ? reason : "PREVISAO_INDISPONIVEL";
        return new AlagamentoPontoDTO(c.floodPointId(), c.slug(), c.name(), "SEM_DADO", null, r, REASONS.get(r), null,
                c.rainLastHourMm(), List.of(), null, c.rainStation(), c.rainStationKm(), null, Map.of(), null, null,
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
                    dbl(h, "rain_q95_mm"), dbl(h, "sheet_q50_cm"), dbl(h, "sheet_q95_cm"), inst(h, "valid_until")));
        }
        Map<String, String> mv = new LinkedHashMap<>();
        JsonNode m = ai.path("model_versions");
        if (m.has("flood")) mv.put("flood", m.path("flood").asText());
        if (m.has("rain")) mv.put("rain", m.path("rain").path("version").asText(null));
        if (m.has("bucket")) mv.put("bucket", m.path("bucket").path("version").asText(null));

        return new AlagamentoPontoDTO(c.floodPointId(), c.slug(), c.name(), level,
                "SEM_DADO".equals(level) ? null : intOrNull(ai, "worst_horizon_hours"), reason,
                reason == null ? null : REASONS.get(reason), dbl(ai, "h_now_cm"), c.rainLastHourMm(), hs,
                explanation(ai.path("explanation")), c.rainStation(), c.rainStationKm(), dbl(ai, "latest_reading_age_minutes"),
                mv, text(ai, "code_version"), inst(ai, "issued_at"), c.evaluatedAt(), true, c.shadow(), RULE,
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
                    dbl(d, "inflow_m3_h"), dbl(d, "outflow_m3_h"), dbl(d, "volume_m3"), dbl(d, "sheet_cm")));
        }
        return new AlagamentoPontoDTO.Explanation(text(e, "served_scenario"), intOrNull(e, "served_horizon_hours"), shap,
                dbl(cf, "extra_mm"), text(cf, "text"), sens, steps, dbl(e, "capacity_m3_h"));
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
