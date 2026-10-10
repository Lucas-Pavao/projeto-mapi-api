package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class UnifiedDataDTO {
    private String floodPointSlug;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    /**
     * Mesmo instante de {@code timestamp}, em ISO-8601 com Z (ADR 0006). O eixo da exportação é UTC e o rótulo é o
     * FIM da hora (ADR 0006 seção 5); vale para os campos {@code weather*} (observation_hourly_v). Os campos
     * {@code sensor*} ainda vêm de sensor_data (hora local sem fuso, UNVERIFIED) e são alinhados a este eixo
     * pela hora "crua": o {@code Z} NÃO atesta o fuso deles. {@code timestamp} segue sem fuso por compatibilidade.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private java.time.Instant timestampUtc;
    
    // Sensores Locais
    private Double sensorPrecipitation;
    private Double sensorWaterLevel;
    private Double sensorSoilHumidity;
    
    // Clima (Open-Meteo)
    private Double weatherPrecipitation;
    private Double weatherTemperature;
    private Double weatherPressure;
    private Integer weatherCode;
    
    /** Umidade do solo 0-7 cm, ERA5-Land (m³/m³); latência ~5 dias, só treino (MODELAGEM-IA A.2.3, feature 22). */
    private Double soilMoisture07cm;

    // Maré
    private Double tideHeight;
    
    // Label LEGADO (flood_events); obsoleto, mantido por compatibilidade (manifesto: deprecatedColumns)
    private Boolean isFlooded;
    private String severity;

    // Rótulo novo (flood_occurrence + occurrence_point_match; MODELAGEM-IA A.3.1). São ALVOS, nunca features.
    /** horizonte (h) -> 1/0; valor nulo = hora excluída (ver labelExcludedReason). */
    private java.util.Map<Integer, Integer> labelByHorizon;
    private Integer labelDay;
    private String labelExcludedReason;
    /** ids de flood_occurrence que justificam o rótulo, separados por ';'. */
    private String occurrenceIds;

    // Features Calculadas
    private Double accumulated3h;
    private Double accumulated6h;
    private Double accumulated12h;
    private Double accumulated24h;
    private Double accumulated48h;
}
