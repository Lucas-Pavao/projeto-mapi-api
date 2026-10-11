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
 * Persiste e relê previsões em {@code flood_predictions} (colunas de auditoria da migração V8).
 *
 * <p>{@code message} guarda só o texto fixo para o operador. Linhas anteriores à V8 (sem
 * {@code model_version}/{@code degradation_reason}) ainda têm auditoria em JSON na {@code message};
 * são lidas por {@link #parseLegacyAudit} até envelhecerem.
 *
 * <p>Fuso: a coluna {@code timestamp} é TIMESTAMP sem fuso (linhas escritas com o fuso da JVM);
 * {@code issued_at} é TIMESTAMPTZ e só existe nas linhas novas. A migração para UTC é a do ADR 0001.
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

    static final String MESSAGE_OK = "Previsão gerada pelo modelo.";

    @Override
    public void record(FloodPredictionRequestDTO request, PredictionResult result, Long floodPointId) {
        try {
            FloodPredictionResponseDTO p = result.response();
            boolean ok = p.riskLevel() != RiskLevel.UNKNOWN;
            String full = result.auditModelVersion();
            FloodPrediction row = FloodPrediction.builder()
                    .timestamp(toLocal(p.issuedAt()))
                    .issuedAt(p.issuedAt())
                    .floodPointId(floodPointId)
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
                    .message(ok || p.degradationReason() == null ? MESSAGE_OK : p.degradationReason().message())
                    .modelVersion(p.modelVersion())
                    .modelVersionFull(full)
                    .horizonHours(p.horizonHours())
                    .degradationReason(p.degradationReason() == null ? null : p.degradationReason().name())
                    .imputedFeatures(p.imputedFeatures().stream()
                            .map(FloodPredictionResponseDTO.ImputedFeature::featureId).toList())
                    .topContributors(p.contributors().stream()
                            .map(c -> new FloodPrediction.Contributor(c.featureId(), c.contribution())).toList())
                    .build();
            repository.save(row);
        } catch (Exception e) {
            log.error("Erro ao salvar histórico de predição no banco: {}", e.getMessage());
        }
    }

    @Override
    public Optional<FloodPredictionResponseDTO> findLatest(Long floodPointId, double latitude, double longitude) {
        Optional<FloodPrediction> latest = floodPointId == null ? Optional.empty()
                : repository.findFirstByFloodPointIdOrderByTimestampDesc(floodPointId);
        if (latest.isEmpty()) {
            latest = repository.findFirstByLatitudeAndLongitudeOrderByTimestampDesc(latitude, longitude);
        }
        if (latest.isEmpty()) return Optional.empty();
        Instant lastValid = findLastValidIssuedAt(floodPointId, latitude, longitude).orElse(null);
        return Optional.of(toResponse(latest.get(), lastValid));
    }

    @Override
    public Optional<Instant> findLastValidIssuedAt(Long floodPointId, double latitude, double longitude) {
        Optional<FloodPrediction> byPoint = floodPointId == null ? Optional.empty()
                : repository.findFirstByFloodPointIdAndStatusOrderByTimestampDesc(floodPointId, STATUS_SUCCESS);
        // Fallback (linhas anteriores à V8, sem flood_point_id): busca antiga por coordenada.
        Optional<FloodPrediction> row = byPoint.isPresent() ? byPoint
                : repository.findFirstByLatitudeAndLongitudeAndStatusOrderByTimestampDesc(latitude, longitude, STATUS_SUCCESS);
        return row.map(this::issuedAtOf);
    }

    private Instant issuedAtOf(FloodPrediction row) {
        return row.getIssuedAt() != null ? row.getIssuedAt() : toInstant(row.getTimestamp());
    }

    /** Auditoria a partir das colunas V8; linha antiga (sem elas) cai no JSON legado da {@code message}. */
    private Audit auditOf(FloodPrediction row) {
        if (row.getModelVersionFull() != null || row.getModelVersion() != null || row.getDegradationReason() != null) {
            return new Audit(row.getModelVersionFull() != null ? row.getModelVersionFull() : row.getModelVersion(),
                    row.getHorizonHours(), row.getDegradationReason(),
                    row.getImputedFeatures() == null ? List.of() : row.getImputedFeatures(),
                    row.getTopContributors() == null ? List.of() : row.getTopContributors().stream()
                            .map(c -> new AuditContributor(c.featureId(), c.contribution())).toList());
        }
        return parseLegacyAudit(row.getMessage());
    }

    private FloodPredictionResponseDTO toResponse(FloodPrediction row, Instant lastValid) {
        Instant issuedAt = issuedAtOf(row);
        Audit audit = auditOf(row);
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

    private Audit parseLegacyAudit(String json) {
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

    /** Auditoria lida das colunas V8 ou do JSON legado de {@code message}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record Audit(String modelVersion, Integer horizonHours, String degradationReason,
                 List<String> imputedFeatures, List<AuditContributor> contributors) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record AuditContributor(String featureId, Double contribution) {
    }
}
