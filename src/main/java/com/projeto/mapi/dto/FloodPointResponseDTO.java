package com.projeto.mapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FloodPointResponseDTO {
    private Long id;
    private String slug;
    private String name;
    private String municipality;
    private String description;
    private Double latitude;
    private Double longitude;
    private Double altitudeM;
    private Double distanceToChannelM;
    private String basinName;
    private FloodPointRequestDTO.SensorConfigDTO sensorConfig;
    private java.util.List<String> nearbySensorIds;
    private MapiResponseDTO.PreciseData liveData;
    // Classificador de alagamento retirado em 21/11/2026 (prompt de 20/11): o campo fica só para uso interno e não sai
    // mais na resposta. O nível do ponto vem de GET /api/alagamento/pontos (alerta por regressão).
    @com.fasterxml.jackson.annotation.JsonIgnore
    private FloodPredictionResponseDTO floodPrediction;
    private Boolean active;
    private Double tideHeight;
    private String tideUnit;
    @io.swagger.v3.oas.annotations.media.Schema(allowableValues = {"OFFICIAL", "HARMONIC"}, nullable = true,
            description = "Fonte da maré em tideHeight: OFFICIAL (tábua oficial TabuaMare/DHN) ou HARMONIC (reserva harmônica, "
                    + "quando não há tábua oficial; vale se qualquer extremo interpolado for harmônico); null sem maré")
    private String tideSource;
}
