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
     * @param rain10min     chuva medida do ponto a cada 10 min, terminando no passo mais novo; {@code null} = lacuna
     * @param terrain       nulo quando o ponto não tem terreno
     * @param terrainStatus OK, SEM_TERRENO ou EM_REVISAO
     */
    JsonNode forecast(double lat, double lon, List<Reading> readings, List<OmHour> openMeteo, List<Double> rain10min,
                      Terrain terrain, String terrainStatus, double trYears, double teMinutes);
}
