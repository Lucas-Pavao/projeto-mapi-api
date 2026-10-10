package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.DegradationReason;
import com.projeto.mapi.dto.FloodPredictionRequestDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.RiskLevel;
import com.projeto.mapi.model.FloodPrediction;
import com.projeto.mapi.repository.FloodPredictionRepository;
import com.projeto.mapi.service.flood.FeatureCatalog;
import com.projeto.mapi.service.flood.FloodPredictionHistoryService;
import com.projeto.mapi.service.flood.PredictionResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Persiste e relê previsões em {@code flood_predictions} SEM migração de schema.
 *
 * <p>A tabela só tem {@code risk_level}/{@code status}/{@code message}. Por isso o que o contrato
 * precisa e a tabela não tem (versão completa do modelo, horizonte, motivo da degradação, imputações,
 * contribuintes) vai como JSON na coluna {@code message} (TEXT). Linhas antigas, com texto livre,
 * são lidas como legado (ver {@link #toResponse}). Dívida registrada: colunas próprias numa migração
 * futura, com aprovação.
 *
 * <p>Fuso: a coluna {@code timestamp} é TIMESTAMP sem fuso e as linhas existentes foram escritas com
 * {@code LocalDateTime.now()} (fuso da JVM). Para ler e escrever de forma consistente com elas, a
 * conversão de/para {@link Instant} usa o fuso da JVM; a migração para UTC é a do ADR 0001.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FloodPredictionHistoryServiceImpl implements FloodPredictionHistoryService {

    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED = "FAILED";

    private final FloodPredictionRepository repository;
    private final FeatureCatalog catalog;
    private final ObjectMapper objectMapper;

    @Override
    public void record(FloodPredictionRequestDTO request, PredictionResult result) {
        try {
            FloodPredictionResponseDTO p = result.response();
            boolean ok = p.riskLevel() != RiskLevel.UNKNOWN;
            FloodPrediction row = FloodPrediction.builder()
                    .timestamp(toLocal(p.issuedAt()))
                    .stationId(request.getStationId())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .currentRainfall(request.getCurrentRainfall())
                    .rainfall3hAccumulated(request.getRainfall3hAccumulated())
                    .rainfall6hAccumulated(request.getRainfall6hAccumulated())
                    .rainfall12hAccumulated(request.getRainfall12hAccumulated())
                    .rainfall24hAccumulated(request.getRainfall24hAccumulated())
                    .tideLevel(request.getTideLevel())
                    .riverLevel(request.getRiverLevel())
                    .floodProbability(p.floodProbability())
                    .riskLevel(p.riskLevel().name())
                    .status(ok ? STATUS_SUCCESS : STATUS_FAILED)
                    .message(objectMapper.writeValueAsString(toAudit(result)))
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.error("Erro ao salvar histórico de predição no banco: {}", e.getMessage());
        }
    }

    @Override
    public Optional<FloodPredictionResponseDTO> findLatest(double latitude, double longitude) {
        Optional<FloodPrediction> latest =
                repository.findFirstByLatitudeAndLongitudeOrderByTimestampDesc(latitude, longitude);
        if (latest.isEmpty()) return Optional.empty();
        Instant lastValid = findLastValidIssuedAt(latitude, longitude).orElse(null);
        return Optional.of(toResponse(latest.get(), lastValid));
    }

    @Override
    public Optional<Instant> findLastValidIssuedAt(double latitude, double longitude) {
        return repository
                .findFirstByLatitudeAndLongitudeAndStatusOrderByTimestampDesc(latitude, longitude, STATUS_SUCCESS)
                .map(r -> toInstant(r.getTimestamp()));
    }

    private Audit toAudit(PredictionResult result) {
        FloodPredictionResponseDTO p = result.response();
        return new Audit(
                result.auditModelVersion(),
                p.horizonHours(),
                p.degradationReason() == null ? null : p.degradationReason().name(),
                p.imputedFeatures().stream().map(FloodPredictionResponseDTO.ImputedFeature::featureId).toList(),
                p.contributors().stream().map(c -> new AuditContributor(c.featureId(), c.contribution())).toList());
    }

    private FloodPredictionResponseDTO toResponse(FloodPrediction row, Instant lastValid) {
        Instant issuedAt = toInstant(row.getTimestamp());
        Audit audit = parseAudit(row.getMessage());
        RiskLevel level = RiskLevel.fromColumn(row.getRiskLevel());
        boolean success = STATUS_SUCCESS.equals(row.getStatus()) && level != RiskLevel.UNKNOWN;

        if (success && audit != null && audit.modelVersion() != null && row.getFloodProbability() != null) {
            String full = audit.modelVersion();
            int plus = full.indexOf('+');
            Instant validUntil = audit.horizonHours() == null || audit.horizonHours() <= 0
                    ? null : issuedAt.plus(audit.horizonHours(), ChronoUnit.HOURS);
            return FloodPredictionResponseDTO.builder()
                    .riskLevel(level)
                    .floodProbability(row.getFloodProbability())
                    .modelVersion(plus >= 0 ? full.substring(0, plus) : full)
                    .issuedAt(issuedAt)
                    .horizonHours(audit.horizonHours())
                    .validUntil(validUntil)
                    .lastValidIssuedAt(lastValid)
                    .contributors(audit.contributors() == null ? List.of() : audit.contributors().stream()
                            .map(c -> catalog.contributor(c.featureId(), c.contribution())).toList())
                    .imputedFeatures(catalog.imputed(audit.imputedFeatures()))
                    .build();
        }

        // Falha registrada, ou linha legada sem versão de modelo (não satisfaz o §2): vira UNKNOWN.
        DegradationReason reason = audit != null && audit.degradationReason() != null
                ? DegradationReason.fromAudit(audit.degradationReason())
                : DegradationReason.AI_ERROR;
        return FloodPredictionResponseDTO.builder()
                .riskLevel(RiskLevel.UNKNOWN)
                .issuedAt(issuedAt)
                .degradationReason(reason)
                .lastValidIssuedAt(lastValid)
                .imputedFeatures(audit == null ? List.of() : catalog.imputed(audit.imputedFeatures()))
                .message(reason.message())
                .build();
    }

    private Audit parseAudit(String json) {
        if (json == null || !json.startsWith("{")) return null;
        try {
            return objectMapper.readValue(json, Audit.class);
        } catch (Exception e) {
            return null;
        }
    }

    private static LocalDateTime toLocal(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    private static Instant toInstant(LocalDateTime local) {
        return local.atZone(ZoneId.systemDefault()).toInstant();
    }

    /** Conteúdo auditável gravado em {@code flood_predictions.message}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Audit(String modelVersion, Integer horizonHours, String degradationReason,
                 List<String> imputedFeatures, List<AuditContributor> contributors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuditContributor(String featureId, Double contribution) {
    }
}
