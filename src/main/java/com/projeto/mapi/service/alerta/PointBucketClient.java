package com.projeto.mapi.service.alerta;

import java.util.List;

/** Chama {@code POST /v1/bucket/point10} da MAPI AI (balde M1a a 10 min). */
public interface PointBucketClient {

    record Terrain(String slug, double areaM2, List<Double> zCurveM, List<Double> vCurveM3, double streetRelM) {}

    record Projection(List<Double> hMm, Integer minutesToHcrit, double hMaxMm) {}

    /** Bloco "limpeza" da IA (mapi_ai/entupimento.py); nulo quando não se mandou dias e taxa. */
    record Cleaning(double daysSinceCleaning, double lambdaRePerDay, double pClogged, double hNowCloggedMm,
                    Double probHcritNow, Double probHcrit60Q50, Integer minutesToHcritQ50Clogged) {}

    record Result(String version, double hNowMm, Projection q50, Projection q80, double iCapMmH, double runtimeMs,
                  Cleaning cleaning) {
        public Result(String version, double hNowMm, Projection q50, Projection q80, double iCapMmH, double runtimeMs) {
            this(version, hNowMm, q50, q80, iCapMmH, runtimeMs, null);
        }
    }

    /** Resultado, ou null se a IA estiver fora ou recusar. Nunca lança. */
    default Result evaluate(List<Double> rain10min, Terrain terrain, double trYears, double teMinutes, Double q50Mm, Double q80Mm) {
        return evaluate(rain10min, terrain, trYears, teMinutes, q50Mm, q80Mm, null, null);
    }

    /** Igual, com a limpeza recente: dias desde a última limpeza e taxa de reentupimento (por dia); nulos = sem limpeza. */
    Result evaluate(List<Double> rain10min, Terrain terrain, double trYears, double teMinutes, Double q50Mm, Double q80Mm,
                    Double daysSinceCleaning, Double lambdaRePerDay);
}
