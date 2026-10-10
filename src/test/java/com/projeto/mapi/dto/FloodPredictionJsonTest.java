package com.projeto.mapi.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Serialização do contrato (ADR 0007 §5) com o ObjectMapper configurado como o da aplicação. */
@JsonTest
class FloodPredictionJsonTest {

    @Autowired
    private ObjectMapper mapper;

    @Test
    void instantesSaemEmIso8601UtcComZ() throws Exception {
        var dto = FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.HIGH).floodProbability(0.91).modelVersion("xgb-h3-2026.11.0")
                .issuedAt(Instant.parse("2026-10-10T12:00:00.987654Z")).horizonHours(3)
                .validUntil(Instant.parse("2026-10-10T15:00:00Z"))
                .lastValidIssuedAt(Instant.parse("2026-10-10T12:00:00Z"))
                .contributors(List.of(new FloodPredictionResponseDTO.Contributor("river_level", "Nível do rio", 0.3)))
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.get("issuedAt").asText()).isEqualTo("2026-10-10T12:00:00Z");
        assertThat(json.get("validUntil").asText()).isEqualTo("2026-10-10T15:00:00Z");
        assertThat(json.get("lastValidIssuedAt").asText()).isEqualTo("2026-10-10T12:00:00Z");
        assertThat(json.get("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(json.get("contributors").get(0).get("featureId").asText()).isEqualTo("river_level");
        assertThat(json.get("contributors").get(0).get("label").asText()).isEqualTo("Nível do rio");
        assertThat(json.get("imputedFeatures").isArray()).isTrue();
        assertThat(json.has("estimatedTimeToEvent")).isTrue();
        assertThat(json.get("estimatedTimeToEvent").isNull()).isTrue();
    }

    @Test
    void unknownSerializaProbabilidadeNulaECamposDeDegradacao() throws Exception {
        var dto = FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.UNKNOWN).issuedAt(Instant.parse("2026-10-10T12:00:00Z"))
                .degradationReason(DegradationReason.AI_TIMEOUT).message(DegradationReason.AI_TIMEOUT.message())
                .build();

        JsonNode json = mapper.readTree(mapper.writeValueAsString(dto));

        assertThat(json.get("riskLevel").asText()).isEqualTo("UNKNOWN");
        assertThat(json.has("floodProbability")).isTrue();
        assertThat(json.get("floodProbability").isNull()).isTrue();
        assertThat(json.get("degradationReason").asText()).isEqualTo("AI_TIMEOUT");
        assertThat(json.get("modelVersion").isNull()).isTrue();
        assertThat(json.get("validUntil").isNull()).isTrue();
        assertThat(json.get("message").asText()).isEqualTo("O modelo de previsão demorou demais para responder.");
    }

    @Test
    void enumDeRiscoSoTemOsQuatroValoresDoContrato() {
        assertThat(RiskLevel.values()).extracting(Enum::name).containsExactly("LOW", "MEDIUM", "HIGH", "UNKNOWN");
        assertThat(DegradationReason.values()).extracting(Enum::name)
                .containsExactly("INSUFFICIENT_DATA", "AI_TIMEOUT", "AI_UNAVAILABLE", "AI_ERROR");
    }
}
