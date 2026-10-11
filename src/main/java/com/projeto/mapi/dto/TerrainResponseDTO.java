package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

/**
 * Terreno do PE3D de um ponto (ADR 0008). Cotas em metros no datum do MDT, que NÃO é comparável ao nível da
 * maré (ADR 0009). Atributos nulos quando {@code status = NO_DATA}, nunca zero.
 */
@Schema(description = "Terreno do PE3D (MDT 1 m, voo de 2016) de um ponto crítico")
public record TerrainResponseDTO(
        String floodPointSlug,
        @Schema(description = "OK ou NO_DATA (ponto fora das folhas do MDT ou sem carga)") String status,
        @Schema(description = "id do terrain_run vigente que originou os valores; nulo se nunca houve carga")
        Long provenanceId,
        @Schema(description = "Cota da rua em metros (mediana do MDT num raio de 3 m), datum do MDT")
        Double streetElevationM,
        @Schema(description = "Declividade média (%) na janela de 100 m") Double meanSlopePct,
        @Schema(description = "Declividade máxima (%) na janela de 100 m") Double maxSlopePct,
        @Schema(description = "Profundidade da depressão no ponto, em metros (fill - MDT)") Double depressionDepthM,
        @Schema(description = "Área contribuinte D8 em m²") Double contributingAreaM2,
        @Schema(description = "Cota do fundo da bacia da curva, em metros") Double bottomElevationM,
        @Schema(description = "Distância fundo-ponto, em metros") Double bottomDistanceM,
        @Schema(description = "Curva cota-volume (41 níveis de 5 cm); vazia se NO_DATA") List<CurvePoint> volumeCurve,
        @Schema(description = "Sensibilidade ao snap e flags de borda da bacia") JsonNode diagnostics,
        Provenance provenance,
        @Schema(description = "Aviso fixo (ADR 0009)") String tideComparabilityWarning) {

    public static final String TIDE_WARNING = "A cota está no datum vertical do MDT (não confirmado) e NÃO é "
            + "comparável ao nível da maré (NR/DHN) até haver referência vertical decidida (ADR 0009).";

    public record CurvePoint(int aboveBottomCm, double elevationM, double cumulativeVolumeM3, Double m3PerCm,
                             Double wetAreaM2) {}

    public record Provenance(String vrtSha256, String scriptSha256, short flightYear, String verticalReference,
                             @JsonFormat(shape = JsonFormat.Shape.STRING) Instant runAt) {}
}
