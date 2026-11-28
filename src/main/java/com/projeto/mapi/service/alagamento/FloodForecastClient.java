package com.projeto.mapi.service.alagamento;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.repository.analise.RainForecastRepository.OmHour;
import com.projeto.mapi.repository.analise.RainForecastRepository.Reading;

import java.util.List;

/**
 * Chama {@code POST /v1/flood/forecast} da MAPI AI (alerta de alagamento por regressão). Nunca lança: falha de rede,
 * timeout ou HTTP de erro devolvem {@code null}, e quem chama grava SEM_DADO com PREVISAO_INDISPONIVEL.
 */
public interface FloodForecastClient {

    /** Terreno do ponto no formato do balde (curva cota-volume acima do fundo e cota da rua menos o fundo). */
    record Terrain(String slug, double areaM2, List<Double> zCurveM, List<Double> vCurveM3, double streetRelM) {}

    /**
     * Fatores por ponto do alerta único (ADR 0030). Modo OFF, INFO ou NIVEL. {@code tideNrM}: maré prevista (m, NR) no
     * fim de cada passo da série medida e dos 36 passos das próximas 6 h; {@code null} num passo = sem maré (a IA devolve
     * SEM_DADO / MARE_AUSENTE com o refluxo no nível). Soleira nula = ponto sem saída cadastrada.
     */
    record Factors(String refluxoMode, List<Double> tideNrM, Double sillMdtM, Double diameterM, Double deltaM, double mdtBiasM,
                   Double mdtBiasScenarioM, String limpezaMode, Double daysSinceCleaning, Double lambdaRePerDay,
                   String clogRegime, Double meanCloggedDays, double fClogged, double probOn) {
        public static final Factors NONE = new Factors("OFF", null, null, null, null, 0.0, null, "OFF", null, null, null,
                null, 0.0, 0.5);
    }

    /**
     * @param rain10min     chuva medida do ponto a cada 10 min, terminando no passo mais novo; {@code null} = lacuna
     * @param terrain       nulo quando o ponto não tem terreno
     * @param terrainStatus OK, SEM_TERRENO ou EM_REVISAO
     */
    default JsonNode forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo, List<Double> rain10min,
                              Terrain terrain, String terrainStatus, double trYears, double teMinutes) {
        return forecast(lat, lon, readings, openMeteo, rain10min, terrain, terrainStatus, trYears, teMinutes, Factors.NONE);
    }

    JsonNode forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo, List<Double> rain10min,
                      Terrain terrain, String terrainStatus, double trYears, double teMinutes, Factors factors);
}
