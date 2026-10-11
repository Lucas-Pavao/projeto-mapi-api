package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** ADR 0007 §2: regras de coerência do payload emitido pelo backend. */
class PredictionCoherenceTest {

    private static final Instant T = Instant.parse("2026-10-10T12:00:00Z");

    private FloodPredictionResponseDTO.FloodPredictionResponseDTOBuilder ok() {
        return FloodPredictionResponseDTO.builder().riskLevel(RiskLevel.MEDIUM).floodProbability(0.42)
                .modelVersion("xgb-h3-2026.11.0").issuedAt(T);
    }

    @Test
    void previsaoValidaECoerente() {
        assertThat(PredictionCoherence.violation(ok().build())).isEmpty();
        assertThat(PredictionCoherence.violation(ok().floodProbability(0.0).build())).isEmpty();
        assertThat(PredictionCoherence.violation(ok().floodProbability(1.0).build())).isEmpty();
    }

    @Test
    void previsaoValidaExigeProbabilidadeNaFaixa() {
        assertThat(PredictionCoherence.violation(ok().floodProbability(null).build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().floodProbability(1.01).build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().floodProbability(-0.1).build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().floodProbability(Double.NaN).build())).isPresent();
    }

    @Test
    void previsaoValidaExigeVersaoEInstanteESemMotivo() {
        assertThat(PredictionCoherence.violation(ok().modelVersion(null).build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().modelVersion(" ").build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().issuedAt(null).build())).isPresent();
        assertThat(PredictionCoherence.violation(ok().degradationReason(DegradationReason.AI_ERROR).build())).isPresent();
    }

    @Test
    void unknownExigeProbabilidadeNulaEMotivo() {
        var unknown = FloodPredictionResponseDTO.builder().riskLevel(RiskLevel.UNKNOWN).issuedAt(T)
                .degradationReason(DegradationReason.AI_TIMEOUT);
        assertThat(PredictionCoherence.violation(unknown.build())).isEmpty();
        assertThat(PredictionCoherence.violation(unknown.floodProbability(0.0).build())).isPresent();
        assertThat(PredictionCoherence.violation(unknown.floodProbability(null).degradationReason(null).build())).isPresent();
    }

    @Test
    void unknownPodeTerHorizonteValidadeEVersaoNulos() {
        var unknown = FloodPredictionResponseDTO.builder().riskLevel(RiskLevel.UNKNOWN)
                .degradationReason(DegradationReason.INSUFFICIENT_DATA).issuedAt(T).build();
        assertThat(unknown.horizonHours()).isNull();
        assertThat(unknown.validUntil()).isNull();
        assertThat(unknown.modelVersion()).isNull();
        assertThat(PredictionCoherence.violation(unknown)).isEmpty();
    }

    @Test
    void payloadOuRiscoNulosSaoIncoerentes() {
        assertThat(PredictionCoherence.violation(null)).isPresent();
        assertThat(PredictionCoherence.violation(ok().riskLevel(null).build())).isPresent();
    }
}
