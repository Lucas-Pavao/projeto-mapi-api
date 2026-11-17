package com.projeto.mapi.service.alerta;

import java.util.List;

/** Chama {@code POST /v1/bucket/point10} da MAPI AI (balde M1a a 10 min). */
public interface PointBucketClient {

    record Terrain(String slug, double areaM2, List<Double> zCurveM, List<Double> vCurveM3, double streetRelM) {}

    record Projection(List<Double> hMm, Integer minutesToHcrit, double hMaxMm) {}

    record Result(String version, double hNowMm, Projection q50, Projection q80, double iCapMmH, double runtimeMs) {}

    /** Resultado, ou null se a IA estiver fora ou recusar. Nunca lança. */
    Result evaluate(List<Double> rain10min, Terrain terrain, double trYears, double teMinutes, Double q50Mm, Double q80Mm);
}
