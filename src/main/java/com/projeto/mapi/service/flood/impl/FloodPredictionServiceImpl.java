package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.projeto.mapi.config.ExternalRestClientFactory;
import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO.Contributor;
import com.projeto.mapi.dto.ModelValidationStatus;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.service.flood.FeatureCatalog;
import com.projeto.mapi.service.flood.FloodPredictionService;
import com.projeto.mapi.service.flood.PredictionCoherence;
import com.projeto.mapi.service.flood.PredictionResult;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Cliente da MAPI AI. Traduz a resposta bruta (snake_case) para o contrato do ADR 0007 e converte
 * toda falha em {@code UNKNOWN} + {@code degradationReason} (§4). O texto da exceção vai só para o
 * log; {@code message} é sempre o texto fixo do motivo.
 */
@Service
@Slf4j
public class FloodPredictionServiceImpl implements FloodPredictionService {

    private final RestClient restClient;
    private final FeatureCatalog catalog;

    @Autowired
    public FloodPredictionServiceImpl(@Value("${app.ai.api-url:http://localhost:8000}") String aiApiUrl,
                                      ExternalRestClientFactory http,
                                      FeatureCatalog catalog) {
        this(http.builder("ai").baseUrl(aiApiUrl).build(), catalog);
    }

    /** Usado pelos testes, que injetam um RestClient ligado a um servidor simulado. */
    public FloodPredictionServiceImpl(RestClient restClient, FeatureCatalog catalog) {
        this.restClient = restClient;
        this.catalog = catalog;
    }

    @Override
    public PredictionResult predict(FloodPredictionRequestDTO request, Collection<String> imputedFeatureIds) {
        log.info("Solicitando predição de alagamento para estação: {}", request.getStationId());
        Instant now = Instant.now();

        try {
            AiPredictionDTO ai = restClient.post()
                    .uri("/v1/predict/flood")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiPredictionDTO.class);

            if (ai == null) {
                log.error("Resposta vazia do modelo de IA (estação {})", request.getStationId());
                return unknown(DegradationReason.AI_ERROR, now, imputedFeatureIds);
            }
            return toResult(ai, now, imputedFeatureIds, request.getStationId());
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.UNPROCESSABLE_ENTITY.value()) {
                log.warn("IA respondeu 422 (dado insuficiente) para a estação {}: {}",
                        request.getStationId(), e.getResponseBodyAsString());
                return unknown(DegradationReason.INSUFFICIENT_DATA, now, imputedFeatureIds);
            }
            log.error("IA respondeu {} para a estação {}: {}", e.getStatusCode(), request.getStationId(), e.getMessage());
            return unknown(DegradationReason.AI_ERROR, now, imputedFeatureIds);
        } catch (HttpServerErrorException e) {
            log.error("IA respondeu {} para a estação {}: {}", e.getStatusCode(), request.getStationId(), e.getMessage());
            return unknown(DegradationReason.AI_ERROR, now, imputedFeatureIds);
        } catch (Exception e) {
            DegradationReason reason = classify(e);
            log.error("Falha ao chamar a IA (estação {}, motivo {}): {}", request.getStationId(), reason, e.toString());
            return unknown(reason, now, imputedFeatureIds);
        }
    }

    /** Classifica falhas de transporte (§4). Qualquer coisa não reconhecida é AI_ERROR. */
    static DegradationReason classify(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof CallNotPermittedException) return DegradationReason.AI_UNAVAILABLE;
            if (t instanceof HttpConnectTimeoutException || t instanceof ConnectException) {
                return DegradationReason.AI_UNAVAILABLE;
            }
            if (t instanceof SocketTimeoutException ste && ste.getMessage() != null
                    && ste.getMessage().toLowerCase().contains("connect")) {
                return DegradationReason.AI_UNAVAILABLE;
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return DegradationReason.AI_TIMEOUT;
            }
        }
        // Rede inalcançável sem causa reconhecida (DNS, reset): a IA não está acessível.
        if (error instanceof ResourceAccessException) return DegradationReason.AI_UNAVAILABLE;
        return DegradationReason.AI_ERROR;
    }

    private PredictionResult toResult(AiPredictionDTO ai, Instant now, Collection<String> imputedByCaller, String station) {
        String problem = null;
        RiskLevel level = RiskLevel.fromAi(ai.riskLevel).orElse(null);
        if (level == null) {
            problem = "risk_level fora de LOW/MEDIUM/HIGH: " + ai.riskLevel;
        } else if (ai.floodProbability == null || ai.floodProbability.isNaN()
                || ai.floodProbability < 0.0 || ai.floodProbability > 1.0) {
            problem = "flood_probability fora de [0,1]: " + ai.floodProbability;
        } else if (ai.modelVersion == null || ai.modelVersion.isBlank()) {
            problem = "model_version ausente";
        } else if (ai.horizonHours != null && ai.horizonHours <= 0) {
            problem = "horizon_hours inválido: " + ai.horizonHours;
        }

        Instant issuedAt = now;
        if (problem == null && ai.issuedAt != null) {
            try {
                issuedAt = OffsetDateTime.parse(ai.issuedAt).toInstant();
            } catch (RuntimeException e) {
                problem = "issued_at ilegível: " + ai.issuedAt;
            }
        }

        List<Contributor> contributors = new ArrayList<>();
        if (problem == null && ai.topContributors != null) {
            for (AiContributor c : ai.topContributors) {
                if (c == null || c.featureId == null || c.featureId.isBlank() || c.contribution == null) {
                    problem = "top_contributors com item inválido";
                    break;
                }
                contributors.add(catalog.contributor(c.featureId, c.contribution));
            }
        }

        List<String> imputedIds = new ArrayList<>(imputedByCaller == null ? List.of() : imputedByCaller);
        if (ai.imputedFeatures != null) imputedIds.addAll(ai.imputedFeatures);

        if (problem != null) {
            log.error("Payload incoerente da IA (estação {}): {}", station, problem);
            return unknown(DegradationReason.AI_ERROR, now, imputedIds);
        }

        String fullVersion = ai.modelVersion.trim();
        int plus = fullVersion.indexOf('+');
        String semanticVersion = plus >= 0 ? fullVersion.substring(0, plus) : fullVersion;
        log.info("Predição recebida (estação {}): modelo completo={} risco={}", station, fullVersion, level);

        ModelValidationStatus validation = ModelValidationStatus.fromAi(ai.modelValidationStatus);
        if (ai.modelValidationStatus != null && validation == ModelValidationStatus.NOT_VALIDATED
                && !ModelValidationStatus.NOT_VALIDATED.name().equalsIgnoreCase(ai.modelValidationStatus.trim())) {
            log.warn("IA enviou model_validation_status={} (estação {}); mantido NOT_VALIDATED (sem ADR de validação)",
                    ai.modelValidationStatus, station);
        }
        Instant validUntil = ai.horizonHours == null ? null : issuedAt.plus(ai.horizonHours, ChronoUnit.HOURS);
        FloodPredictionResponseDTO response = FloodPredictionResponseDTO.builder()
                .riskLevel(level)
                .floodProbability(ai.floodProbability)
                .modelVersion(semanticVersion)
                .issuedAt(issuedAt)
                .horizonHours(ai.horizonHours)
                .validUntil(validUntil)
                .lastValidIssuedAt(issuedAt)
                .contributors(contributors)
                .imputedFeatures(catalog.imputed(imputedIds))
                .modelValidationStatus(validation)
                .build();

        // Cinto e suspensório: o que sai daqui nunca viola o §2.
        if (PredictionCoherence.violation(response).isPresent()) {
            log.error("Payload montado violou a coerência: {}", PredictionCoherence.violation(response).get());
            return unknown(DegradationReason.AI_ERROR, now, imputedIds);
        }
        return new PredictionResult(response, fullVersion);
    }

    private PredictionResult unknown(DegradationReason reason, Instant now, Collection<String> imputedIds) {
        FloodPredictionResponseDTO response = FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.UNKNOWN)
                .issuedAt(now)
                .degradationReason(reason)
                .imputedFeatures(catalog.imputed(imputedIds))
                .message(reason.message())
                .build();
        return new PredictionResult(response, null);
    }

    // Shape bruto devolvido pela MAPI AI (Python/FastAPI, snake_case), só usado aqui. Os campos
    // novos do ADR 0007 §3 são opcionais para o parse; a obrigatoriedade é validada em toResult.
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiPredictionDTO {
        @JsonProperty("flood_probability")
        private Double floodProbability;

        @JsonProperty("risk_level")
        private String riskLevel;

        @JsonProperty("model_version")
        private String modelVersion;

        @JsonProperty("model_validation_status")
        private String modelValidationStatus;

        @JsonProperty("horizon_hours")
        private Integer horizonHours;

        @JsonProperty("issued_at")
        private String issuedAt;

        @JsonProperty("top_contributors")
        private List<AiContributor> topContributors;

        @JsonProperty("imputed_features")
        private List<String> imputedFeatures;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AiContributor {
        @JsonProperty("feature_id")
        private String featureId;

        private Double contribution;
    }
}
