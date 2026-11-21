package com.projeto.mapi.service.alert.impl;

import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.mapper.AlertMapper;
import com.projeto.mapi.model.Alert;
import com.projeto.mapi.model.AlertKind;
import com.projeto.mapi.model.AlertRule;
import com.projeto.mapi.model.AlertState;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.repository.AlertRepository;
import com.projeto.mapi.repository.AlertRuleRepository;
import com.projeto.mapi.repository.AlertStateRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.alert.AlertCreatedEvent;
import com.projeto.mapi.service.alert.AlertEvaluator;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import com.projeto.mapi.service.sensor.HourlyRainService;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.tide.TideService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertEvaluatorImpl implements AlertEvaluator {

    static final Duration SUPPRESSION = Duration.ofMinutes(30);
    static final double DEFAULT_STALE_MINUTES = 30.0;

    private final AlertRuleRepository ruleRepository;
    private final AlertRepository alertRepository;
    private final AlertStateRepository stateRepository;
    private final FloodPointRepository pointRepository;
    private final FloodForecastService floodForecast;
    private final SensorService sensorService;
    private final HourlyRainService hourlyRain;
    private final TideService tideService;
    private final AlertMapper alertMapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    /** Resultado da avaliação de uma regra para um alvo. */
    // Classe (e não record): o accessor público de um record exporia tipos de model.. (regra do ArchUnit, ADR 0002).
    private static final class Candidate {
        final String targetKey;
        final FloodPoint point;
        final String stationId;
        final boolean inCondition;
        final Alert.Severity severity;
        final String title;
        final String detail;
        final Double observedValue;
        final String riskLevel;
        final String degradationReason;
        final String modelValidationStatus;

        Candidate(String targetKey, FloodPoint point, String stationId, boolean inCondition,
                  Alert.Severity severity, String title, String detail, Double observedValue,
                  String riskLevel, String degradationReason, String modelValidationStatus) {
            this.targetKey = targetKey;
            this.point = point;
            this.stationId = stationId;
            this.inCondition = inCondition;
            this.severity = severity;
            this.title = title;
            this.detail = detail;
            this.observedValue = observedValue;
            this.riskLevel = riskLevel;
            this.degradationReason = degradationReason;
            this.modelValidationStatus = modelValidationStatus;
        }
    }

    /** Dados lidos uma vez por avaliação (cada regra os reaproveita). */
    private static final class Snapshot {
        final List<FloodPoint> points;
        final Map<String, AlagamentoPontoDTO> flood;
        final List<SensorResponseDTO> sensors;
        final List<HourlyRainService.StationRain> rainLastHour;

        Snapshot(List<FloodPoint> points, Map<String, AlagamentoPontoDTO> flood,
                 List<SensorResponseDTO> sensors, List<HourlyRainService.StationRain> rainLastHour) {
            this.points = points;
            this.flood = flood;
            this.sensors = sensors;
            this.rainLastHour = rainLastHour;
        }
    }

    @Override
    @Transactional
    public int evaluate() {
        List<AlertRule> rules = ruleRepository.findByEnabledTrueOrderByIdAsc();
        if (rules.isEmpty()) return 0;
        Instant now = clock.instant();
        Snapshot snap = new Snapshot(
                pointRepository.findByActiveTrue(),
                // Alerta por regressão (V38). Em modo sombra fica vazio: a Central não mostra a operadores o que só o ADMIN vê.
                floodForecast.shadow() ? Map.of() : floodForecast.current().stream()
                        .collect(Collectors.toMap(AlagamentoPontoDTO::slug, Function.identity(), (a, b) -> a)),
                sensorService.getLatestWithFreshness(null),
                hourlyRain.latestClosedHour());

        int created = 0;
        for (AlertRule rule : rules) {
            try {
                for (Candidate c : candidates(rule, snap)) {
                    if (process(rule, c, now)) created++;
                }
            } catch (Exception e) {
                log.error("Falha ao avaliar a regra {} ({}): {}", rule.getId(), rule.getKind(), e.getMessage(), e);
            }
        }
        if (created > 0) log.info("Avaliador de alertas: {} alerta(s) novo(s)", created);
        return created;
    }

    /**
     * Grava o alerta quando o alvo ENTRA em condição ou ESCALA de severidade dentro dela (ex.: MEDIUM para HIGH numa
     * regra com minRisk MEDIUM). A supressão de 30 min vale para o mesmo nível: um alerta recente de severidade igual
     * ou maior segura o novo; um de severidade menor não. O estado é sempre atualizado.
     */
    private boolean process(AlertRule rule, Candidate c, Instant now) {
        AlertState.Key key = new AlertState.Key(rule.getId(), c.targetKey);
        AlertState previous = stateRepository.findById(key).orElse(null);
        boolean wasIn = previous != null && previous.isInCondition();
        boolean entered = c.inCondition && !wasIn;
        boolean escalated = c.inCondition && wasIn && previous.getSeverity() != null
                && c.severity.ordinal() > previous.getSeverity().ordinal();
        boolean createdAlert = false;
        // Saída do alerta por regressão: um aviso INFO "saiu do alerta" (5.10), com a mesma supressão.
        if (rule.getKind() == AlertKind.RISK_LEVEL && wasIn && !c.inCondition && c.point != null) {
            Alert exit = alertRepository.save(Alert.builder()
                    .rule(rule).kind(rule.getKind()).severity(Alert.Severity.INFO)
                    .floodPoint(c.point).title(c.point.getName() + ": saiu do alerta")
                    .detail("Alerta experimental (chuva prevista + balde), não validado.")
                    .observedValue(c.observedValue).riskLevel(c.riskLevel).triggeredAt(now).build());
            publisher.publishEvent(new AlertCreatedEvent(alertMapper.toDto(exit)));
            createdAlert = true;
        }
        if ((entered || escalated) && !suppressed(rule, c, now)) {
            Alert alert = alertRepository.save(Alert.builder()
                    .rule(rule).kind(rule.getKind()).severity(c.severity)
                    .floodPoint(c.point).stationId(c.stationId)
                    .title(c.title).detail(c.detail).observedValue(c.observedValue)
                    .riskLevel(c.riskLevel).degradationReason(c.degradationReason)
                    .modelValidationStatus(c.modelValidationStatus)
                    .triggeredAt(now).build());
            publisher.publishEvent(new AlertCreatedEvent(alertMapper.toDto(alert)));
            createdAlert = true;
        }
        stateRepository.save(AlertState.builder().ruleId(rule.getId()).targetKey(c.targetKey)
                .inCondition(c.inCondition).severity(c.inCondition ? c.severity : null).updatedAt(now).build());
        return createdAlert;
    }

    private boolean suppressed(AlertRule rule, Candidate c, Instant now) {
        Instant since = now.minus(SUPPRESSION);
        // severidades iguais ou maiores que a do candidato
        List<Alert.Severity> sameOrHigher = java.util.Arrays.stream(Alert.Severity.values())
                .filter(sv -> sv.ordinal() >= c.severity.ordinal()).toList();
        if (c.point != null) {
            return alertRepository.existsByRule_IdAndFloodPoint_IdAndSeverityInAndTriggeredAtGreaterThanEqual(
                    rule.getId(), c.point.getId(), sameOrHigher, since);
        }
        return c.stationId != null
                && alertRepository.existsByRule_IdAndStationIdAndSeverityInAndTriggeredAtGreaterThanEqual(
                        rule.getId(), c.stationId, sameOrHigher, since);
    }

    private List<Candidate> candidates(AlertRule rule, Snapshot s) {
        List<Candidate> out = new ArrayList<>();
        AlertKind kind = rule.getKind();
        switch (kind) {
            case RISK_LEVEL, PREDICTION_UNAVAILABLE, TIDE_ABOVE -> {
                for (FloodPoint p : s.points) {
                    if (rule.getFloodPoint() != null && !rule.getFloodPoint().getId().equals(p.getId())) continue;
                    Candidate c = switch (kind) {
                        case RISK_LEVEL -> riskCandidate(rule, p, s.flood.get(p.getSlug()));
                        case PREDICTION_UNAVAILABLE -> unavailableCandidate(p, s.flood.get(p.getSlug()));
                        default -> tideCandidate(rule, p);
                    };
                    if (c != null) out.add(c);
                }
            }
            case RIVER_STAGE -> {
                for (SensorResponseDTO sensor : s.sensors) {
                    if (!matchesStation(rule, sensor.getSensorId()) || sensor.getWaterLevel() == null) continue;
                    Candidate c = riverCandidate(rule, sensor);
                    if (c != null) out.add(c);
                }
            }
            case RAIN_RATE -> {
                if (rule.getThreshold() == null) break;
                // Soma horária de observation.precipitation (série consolidada, só ACTIVE), última hora fechada.
                for (HourlyRainService.StationRain r : s.rainLastHour) {
                    if (rule.getStationId() != null && !HourlyRainService.sameStation(r.stationId(), rule.getStationId())) continue;
                    boolean in = r.mm() >= rule.getThreshold();
                    out.add(new Candidate("station:" + r.stationId(), null, r.stationId(), in, Alert.Severity.WARNING,
                            "Chuva forte no pluviômetro " + r.stationId(),
                            String.format(Locale.ROOT, "%.1f mm na hora encerrada em %s (soma horária; limiar %.1f mm/h; fonte: %s).",
                                    r.mm(), r.hourEnd(), rule.getThreshold(), rule.getThresholdSource()),
                            r.mm(), null, null, null));
                }
            }
            case SENSOR_STALE -> {
                double minutes = rule.getThreshold() != null ? rule.getThreshold() : DEFAULT_STALE_MINUTES;
                for (SensorResponseDTO sensor : s.sensors) {
                    if (sensor.getAgeSeconds() == null) continue;
                    boolean wanted = rule.getStationId() != null
                            ? rule.getStationId().equals(sensor.getSensorId())
                            : "PRECIPITATION".equals(sensor.getType());
                    if (!wanted) continue;
                    boolean in = sensor.getAgeSeconds() > minutes * 60;
                    out.add(new Candidate("station:" + sensor.getSensorId(), null, sensor.getSensorId(), in, Alert.Severity.WARNING,
                            "Sensor sem resposta: " + sensor.getSensorId(),
                            String.format(Locale.ROOT, "Última leitura há %d min (limite %.0f min).",
                                    sensor.getAgeSeconds() / 60, minutes),
                            sensor.getAgeSeconds() / 60.0, null, null, null));
                }
            }
        }
        return out;
    }

    /**
     * Regra "Atenção ou alerta" (min_risk MEDIUM) ou "Só alerta" (min_risk HIGH) sobre o nível do alerta por regressão.
     * SEM_DADO nunca é saída do alerta: não muda o estado (como o UNKNOWN da ADR 0011).
     */
    private Candidate riskCandidate(AlertRule rule, FloodPoint p, AlagamentoPontoDTO a) {
        if (a == null || "SEM_DADO".equals(a.level())) return null;
        boolean onlyAlert = "HIGH".equals(rule.getMinRisk());
        boolean in = "ALERTA".equals(a.level()) || (!onlyAlert && "ATENCAO".equals(a.level()));
        Alert.Severity severity = "ALERTA".equals(a.level()) ? Alert.Severity.CRITICAL : Alert.Severity.WARNING;
        String what = "ALERTA".equals(a.level()) ? "alerta" : "ATENCAO".equals(a.level()) ? "atenção" : "sem alerta";
        String title = String.format(Locale.forLanguageTag("pt-BR"), "%s: %s em %s h%s", p.getName(), what,
                a.worstHorizonHours() == null ? "?" : a.worstHorizonHours(),
                a.hNowCm() == null ? "" : String.format(Locale.forLanguageTag("pt-BR"), " · lâmina %.1f cm", a.hNowCm()));
        return new Candidate("point:" + p.getSlug(), p, null, in, severity, title,
                "Alerta experimental (chuva prevista + balde), não validado.", a.hNowCm(), a.level(), null, "NOT_VALIDATED");
    }

    private Candidate unavailableCandidate(FloodPoint p, AlagamentoPontoDTO a) {
        if (a == null) return null;
        boolean in = "SEM_DADO".equals(a.level());
        return new Candidate("point:" + p.getSlug(), p, null, in, Alert.Severity.WARNING,
                p.getName() + ": alerta sem dado",
                "Sem dado" + (a.reasonText() != null ? " (" + a.reasonText() + ")" : "") + ". Sem dado não é sem alerta.",
                null, "SEM_DADO", a.reason(), "NOT_VALIDATED");
    }

    private Candidate tideCandidate(AlertRule rule, FloodPoint p) {
        if (rule.getThreshold() == null) return null;
        Double tide = tideService.getCurrentTideHeight(p.getLatitude(), p.getLongitude());
        if (tide == null) return null;
        return new Candidate("point:" + p.getSlug(), p, null, tide >= rule.getThreshold(), Alert.Severity.WARNING,
                "Maré acima do limiar em " + p.getName(),
                String.format(Locale.ROOT, "Maré prevista %.2f m (limiar %.2f m; fonte: %s).",
                        tide, rule.getThreshold(), rule.getThresholdSource()),
                tide, null, null, null);
    }

    private Candidate riverCandidate(AlertRule rule, SensorResponseDTO sensor) {
        Double limit = rule.getThreshold() != null ? rule.getThreshold() : sensor.getRiverAlertLevel();
        if (limit == null) return null;
        double level = sensor.getWaterLevel();
        Alert.Severity severity = sensor.getRiverFloodLevel() != null && level >= sensor.getRiverFloodLevel()
                ? Alert.Severity.CRITICAL : Alert.Severity.WARNING;
        return new Candidate("station:" + sensor.getSensorId(), null, sensor.getSensorId(), level >= limit, severity,
                "Rio na cota de alerta: " + (sensor.getStationName() != null ? sensor.getStationName() : sensor.getSensorId()),
                String.format(Locale.ROOT, "Nível %.2f m (cota de alerta %.2f m).", level, limit),
                level, null, null, null);
    }

    private static boolean matchesStation(AlertRule rule, String stationId) {
        return rule.getStationId() == null || rule.getStationId().equals(stationId);
    }
}
