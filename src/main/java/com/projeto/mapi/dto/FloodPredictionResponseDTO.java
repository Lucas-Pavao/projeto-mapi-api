package com.projeto.mapi.dto;

import lombok.Builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Contrato de SAÍDA para o front (camelCase), ADR 0007 §5. Não confundir com o shape bruto da MAPI AI
 * (snake_case, ver FloodPredictionServiceImpl.AiPredictionDTO).
 *
 * <p>As regras de coerência (§2) são conferidas por {@code PredictionCoherence}. Todo instante é UTC e
 * serializa em ISO-8601 com "Z"; é truncado em segundos para a resposta ficar estável.
 *
 * <p>{@code modelValidationStatus} (Emenda 1 do ADR 0007) é sempre preenchido e vale {@code NOT_VALIDATED}
 * por padrão; {@code VALIDATED} só com ADR que cite avaliação pré-registrada positiva (ver {@link ModelValidationStatus}).
 */
@Builder(toBuilder = true)
public record FloodPredictionResponseDTO(
        RiskLevel riskLevel,
        Double floodProbability,
        String modelVersion,
        Instant issuedAt,
        Integer horizonHours,
        Instant validUntil,
        DegradationReason degradationReason,
        Instant lastValidIssuedAt,
        List<Contributor> contributors,
        List<ImputedFeature> imputedFeatures,
        @Deprecated String estimatedTimeToEvent,
        String message,
        ModelValidationStatus modelValidationStatus,
        RiskThresholds riskThresholds,
        String rainSource,
        String trainingData,
        String syntheticWarning,
        RainInput rainInput,
        // Modelo v3 (C1), por adição: que probabilidade floodProbability é (PRIOR_CORRECTED = calibrada e corrigida
        // para a prevalência real declarada; RAW no v2) e a não corrigida, para o relatório.
        String probabilityKind,
        Double floodProbabilityUncorrected,
        // Plano de 16/11, fase 4, por adição: a previsão de chuva do ponto emitida no mesmo ciclo. Nulo se não houve.
        RainForecastUsed rainForecast) {

    public FloodPredictionResponseDTO {
        modelValidationStatus = modelValidationStatus == null ? ModelValidationStatus.NOT_VALIDATED : modelValidationStatus;
        issuedAt = truncate(issuedAt);
        validUntil = truncate(validUntil);
        lastValidIssuedAt = truncate(lastValidIssuedAt);
        contributors = contributors == null ? List.of() : List.copyOf(contributors);
        imputedFeatures = imputedFeatures == null ? List.of() : List.copyOf(imputedFeatures);
    }

    private static Instant truncate(Instant i) {
        return i == null ? null : i.truncatedTo(ChronoUnit.SECONDS);
    }

    /**
     * Fator que mais pesou na previsão; {@code label} vem do catálogo fixo em português. {@code value} e {@code unit}
     * (Etapa 8 do IN1165) trazem o valor da feature em unidade física; nulos quando a IA não os envia.
     */
    public record Contributor(String featureId, String label, Double contribution, Double value, String unit) {
        public Contributor(String featureId, String label, Double contribution) {
            this(featureId, label, contribution, null, null);
        }
    }

    /**
     * Limiares de probabilidade que produziram a faixa (Etapa 8 do IN1165). Vêm do metadata do modelo, pela IA:
     * o backend não tem limiar próprio. {@code source} = {@code metadata} (escolhidos na validação) ou {@code legacy}.
     */
    /**
     * Chuva enviada ao modelo (ADR 0007, emenda 3): fonte, hora de referência, idade da leitura mais recente e, por
     * entrada, o valor (nulo = ausente) e as estações que o forneceram. Nulo em previsões anteriores à emenda.
     */
    public record RainInput(String source, Instant hourEndUtc, Instant latestReadingUtc, Long ageMinutes,
                            Integer validStations, List<RainValue> values) {
    }

    public record RainValue(String featureId, Double valueMm, Integer hours, Integer hoursWithValue,
                            List<RainStation> stations) {
    }

    public record RainStation(String stationId, String name, String source) {
    }

    /**
     * Previsão de chuva do ponto no ciclo desta previsão (modelo rain_v1; alvo medido, sem etiqueta de sintético).
     * {@code usedByModel}: verdadeiro só quando o modelo de alagamento servido a recebe como entrada e declara o uso;
     * o logreg_v3 não a usa.
     */
    public record RainForecastUsed(Instant issuedAt, String modelVersion, List<String> sources, boolean usedByModel,
                                   List<RainForecastHorizon> horizons) {
    }

    public record RainForecastHorizon(int horizonHours, double q50Mm, double q80Mm, double q95Mm, double probMedia,
                                      double probForte) {
    }

    public record RiskThresholds(Double medium, Double high, String source) {
    }

    /** Variável que o backend (ou a IA) preencheu por imputação, em vez de medir. */
    public record ImputedFeature(String featureId, String label) {
    }
}
