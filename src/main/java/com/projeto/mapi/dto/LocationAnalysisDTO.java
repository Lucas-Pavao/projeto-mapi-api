package com.projeto.mapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Análise de um local qualquer do mapa: chuva (regra do ponto, raio de 5 km), ponto crítico mais próximo com a última
 * previsão gravada, maré, régua de rio, terreno e clima. Ausência = null, nunca zero.
 */
@Schema(description = "Análise de local. lat/lon devolvem o centro da célula de ~200 m usada no cálculo e no cache de 10 min")
public record LocationAnalysisDTO(
        @Schema(description = "Latitude do centro da célula de ~200 m") double lat,
        @Schema(description = "Longitude do centro da célula de ~200 m") double lon,
        @Schema(description = "Instante de referência (UTC); a chuva vale para a última hora fechada até ele") Instant at,
        @Schema(description = "Dentro do retângulo da RMR (lat -8.4 a -7.8, lon -35.2 a -34.8), o mesmo da avaliação pública de risco (PublicRiskService)") boolean inArea,
        @Schema(description = "Mar: dentro da área, fora do MDT do PE3D e com altitude do Open-Meteo menor ou igual a 0") boolean isSea,
        Rain rain,
        @Schema(description = "Ponto crítico ativo mais próximo, sem limite de distância; null se não há pontos") NearestPoint nearestPoint,
        @Schema(description = "Maré prevista (tábua local); null sem tábua para o dia") Tide tide,
        @Schema(description = "Régua da APAC mais próxima a até 6 km; null se não há") River river,
        @Schema(description = "Cota do MDT do PE3D; null fora da cobertura") Terrain terrain,
        @Schema(description = "Clima atual do Open-Meteo; null se indisponível") Weather weather,
        @Schema(description = "Citação obrigatória dos dados do Cemaden") String attribution) {

    @Schema(description = "mode: MEASURED (estação válida com valor na hora) | GAP (válidas, nenhuma com valor; valores null, sem estimativa) "
            + "| ESTIMATED (nenhuma estação válida em 5 km; Open-Meteo, station null)")
    public record Rain(
            String mode,
            @Schema(description = "Chuva da última hora fechada (mm)") Double nowMmH,
            @Schema(description = "Soma das 3 últimas horas fechadas (mm); null se faltar hora") Double acc3hMm,
            @Schema(description = "Soma das 24 últimas horas fechadas (mm); null se faltar hora") Double acc24hMm,
            @Schema(description = "Estação que deu o valor (maior valor entre as válidas, ADR 0022); null em GAP e ESTIMATED") Station station,
            @Schema(description = "Todas as estações a até 5 km, com o papel de cada uma") List<Candidate> candidates,
            @Schema(description = "Estações válidas (mês OK ou UNCHECKED) no raio") int nValid) {
    }

    public record Station(String stationId, String name, double distanceM) {
    }

    @Schema(description = "role: USED | LOWER | EXCLUDED (mês FAIL/OFFLINE) | NO_VALUE")
    public record Candidate(String stationId, String name, double distanceM, Double valueMmH, String monthStatus,
                            List<String> reasons, String role, double lat, double lon) {
    }

    public record NearestPoint(String slug, String name, double distanceKm,
                               @Schema(description = "LOW | MEDIUM | HIGH | UNKNOWN") String riskLevel,
                               Double probability, String degradationReason) {
    }

    public record Tide(Double levelM, Boolean rising, String harbor, String verticalReference) {
    }

    public record River(String stationId, String name, Double levelM, double distanceKm,
                        @Schema(description = "Sempre true: a régua não entra no modelo de previsão") boolean outsideModel) {
    }

    public record Terrain(Double elevationM,
                          @Schema(description = "Sem camada de linha de costa no sistema; sempre null por ora") Double distanceToCoastKm) {
    }

    public record Weather(Double temperatureC, Double humidityPct, Double windKmh, String source) {
    }
}
