package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Contrato BucketSimulation. Simulação ilustrativa do M1a, não é previsão (ADR 0014). */
public record BucketSimulationDTO(
        String model,
        boolean illustrative,
        List<Double> hMm,
        double hmaxMm,
        /** Índice 0-based em rainfallMmH da primeira hora com h >= hcrit; nulo se nunca cruza. */
        Integer tCrossH,
        int nOutsideCurve,
        ParamsUsed paramsUsed) {

    /** Chaves iguais às de {@code Params} do balde.py. */
    public record ParamsUsed(
            @JsonProperty("C") double c,
            @JsonProperty("iT_mm_h") double iTmmH,
            @JsonProperty("f") double f,
            @JsonProperty("hcrit_mm") double hcritMm,
            @JsonProperty("snap_m") int snapM,
            @JsonProperty("curve_scale") double curveScale) {
    }
}
