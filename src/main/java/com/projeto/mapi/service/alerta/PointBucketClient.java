package com.projeto.mapi.service.alerta;

import java.util.List;

/** Chama {@code POST /v1/bucket/point10} da MAPI AI (balde M1a a 10 min). */
public interface PointBucketClient {

    record Terrain(String slug, double areaM2, List<Double> zCurveM, List<Double> vCurveM3, double streetRelM) {}

    record Projection(List<Double> hMm, Integer minutesToHcrit, double hMaxMm) {}

    /** Bloco "limpeza" da IA (mapi_ai/entupimento.py); nulo quando não se mandou dias e taxa. */
    record Cleaning(double daysSinceCleaning, double lambdaRePerDay, double pClogged, double hNowCloggedMm,
                    Double probHcritNow, Double probHcrit60Q50, Integer minutesToHcritQ50Clogged) {}

    /** Bloco "refluxo" da IA; nulo sem maré. */
    record Backflow(double factorNow, double factorMinProjection, double tideNowNrM, double sillNrM, double diameterM) {}

    record Result(String version, double hNowMm, Projection q50, Projection q80, double iCapMmH, double runtimeMs,
                  Cleaning cleaning, Backflow backflow) {
        public Result(String version, double hNowMm, Projection q50, Projection q80, double iCapMmH, double runtimeMs) {
            this(version, hNowMm, q50, q80, iCapMmH, runtimeMs, null, null);
        }
    }

    /** Limpeza recente e refluxo de maré; cada parte nula = desligada. tideNrM: um valor por passo da série + 6 da projeção. */
    record Extras(Double daysSinceCleaning, Double lambdaRePerDay, List<Double> tideNrM, Double outletSillNrM,
                  Double outletDiameterM) {
        public boolean empty() {
            return (daysSinceCleaning == null || lambdaRePerDay == null) && tideNrM == null;
        }
    }

    /** Resultado, ou null se a IA estiver fora ou recusar. Nunca lança. */
    default Result evaluate(List<Double> rain10min, Terrain terrain, double trYears, double teMinutes, Double q50Mm, Double q80Mm) {
        return evaluate(rain10min, terrain, trYears, teMinutes, q50Mm, q80Mm, null);
    }

    /** Igual, com limpeza recente e/ou refluxo de maré. */
    Result evaluate(List<Double> rain10min, Terrain terrain, double trYears, double teMinutes, Double q50Mm, Double q80Mm,
                    Extras extras);
}
