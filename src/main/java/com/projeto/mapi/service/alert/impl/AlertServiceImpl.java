package com.projeto.mapi.service.alert.impl;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.AlertRuleDTO;
import com.projeto.mapi.dto.AlertRuleInputDTO;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.mapper.AlertMapper;
import com.projeto.mapi.model.Alert;
import com.projeto.mapi.model.AlertKind;
import com.projeto.mapi.model.AlertRule;
import com.projeto.mapi.repository.AlertRepository;
import com.projeto.mapi.repository.AlertRuleRepository;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.service.alert.AlertService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AlertServiceImpl implements AlertService {

    private final AlertRepository alertRepository;
    private final AlertRuleRepository ruleRepository;
    private final FloodPointRepository pointRepository;
    private final AlertMapper mapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public PageResponseDTO<AlertDTO> list(String status, Instant from, Instant to, int page, int size) {
        String st = status == null ? "OPEN" : status;
        if (!List.of("OPEN", "ACKNOWLEDGED", "ALL").contains(st)) {
            throw new InvalidRequestException("status deve ser OPEN, ACKNOWLEDGED ou ALL");
        }
        Specification<Alert> spec = Specification.where(null);
        if (st.equals("OPEN")) spec = spec.and((r, q, cb) -> cb.isNull(r.get("acknowledgedAt")));
        if (st.equals("ACKNOWLEDGED")) spec = spec.and((r, q, cb) -> cb.isNotNull(r.get("acknowledgedAt")));
        if (from != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("triggeredAt"), from));
        if (to != null) spec = spec.and((r, q, cb) -> cb.lessThan(r.get("triggeredAt"), to));
        var result = alertRepository.findAll(spec,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "triggeredAt", "id")));
        return new PageResponseDTO<>(result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getContent().stream().map(mapper::toDto).toList());
    }

    @Override
    @Transactional
    public AlertDTO acknowledge(Long id, String username) {
        Alert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alerta não encontrado: " + id));
        if (alert.getAcknowledgedAt() == null) {
            alert.setAcknowledgedAt(clock.instant());
            alert.setAcknowledgedBy(username);
            alert = alertRepository.save(alert);
        }
        return mapper.toDto(alert);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AlertRuleDTO> listRules() {
        return ruleRepository.findAllByOrderByIdAsc().stream().map(mapper::toDto).toList();
    }

    @Override
    @Transactional
    public AlertRuleDTO createRule(AlertRuleInputDTO input, String username) {
        AlertRule rule = AlertRule.builder().createdBy(username).createdAt(clock.instant()).build();
        apply(rule, input);
        return mapper.toDto(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public AlertRuleDTO updateRule(Long id, AlertRuleInputDTO input) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regra não encontrada: " + id));
        apply(rule, input);
        rule.setUpdatedAt(clock.instant());
        return mapper.toDto(ruleRepository.save(rule));
    }

    @Override
    @Transactional
    public void deleteRule(Long id) {
        AlertRule rule = ruleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Regra não encontrada: " + id));
        ruleRepository.delete(rule);
    }

    private void apply(AlertRule rule, AlertRuleInputDTO in) {
        AlertKind kind = AlertKind.valueOf(in.kind());
        switch (kind) {
            case RISK_LEVEL -> require(in.minRisk() != null, "RISK_LEVEL exige minRisk (MEDIUM ou HIGH)");
            case TIDE_ABOVE, RAIN_RATE -> {
                require(in.threshold() != null && in.threshold() > 0, kind + " exige threshold maior que zero");
                require(in.thresholdSource() != null && !in.thresholdSource().isBlank(),
                        kind + " exige thresholdSource (origem do limiar)");
            }
            case SENSOR_STALE -> require(in.threshold() != null && in.threshold() > 0,
                    "SENSOR_STALE exige threshold em minutos, maior que zero");
            default -> { }
        }
        rule.setKind(kind);
        rule.setFloodPoint(in.floodPointSlug() == null ? null : pointRepository.findBySlug(in.floodPointSlug())
                .orElseThrow(() -> new InvalidRequestException("Ponto não encontrado: " + in.floodPointSlug())));
        rule.setStationId(in.stationId());
        rule.setThreshold(in.threshold());
        rule.setThresholdSource(in.thresholdSource());
        rule.setMinRisk(in.minRisk());
        rule.setEnabled(in.enabled());
        rule.setChannels(in.channels() == null || in.channels().isEmpty() ? "BROWSER" : String.join(",", in.channels()));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new InvalidRequestException(message);
    }
}
