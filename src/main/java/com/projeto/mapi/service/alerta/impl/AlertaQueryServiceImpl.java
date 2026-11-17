package com.projeto.mapi.service.alerta.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.config.AlertaProperties;
import com.projeto.mapi.dto.AlertaLatenciaDTO;
import com.projeto.mapi.dto.AlertaPontoDTO;
import com.projeto.mapi.dto.AlertaTransicaoDTO;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.alerta.AlertaRepository;
import com.projeto.mapi.service.alerta.AlertaQueryService;
import com.projeto.mapi.service.alerta.SemDadoMotivo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AlertaQueryServiceImpl implements AlertaQueryService {

    private final AlertaRepository repo;
    private final FloodPointRepository points;
    private final AlertaProperties props;
    private final ObjectMapper mapper;
    private final Clock clock;

    @Override
    public boolean shadow() {
        return props.isShadow();
    }

    @Override
    public List<AlertaPontoDTO> current() {
        return repo.states().stream().map(this::dto).toList();
    }

    private static Double cm(Double mm) {
        return mm == null ? null : Math.round(mm) / 10.0;
    }

    private static String text(String reason) {
        if (reason == null) return null;
        try {
            return SemDadoMotivo.valueOf(reason).texto();
        } catch (IllegalArgumentException e) {
            return reason;
        }
    }

    private AlertaPontoDTO dto(AlertaRepository.State s) {
        return new AlertaPontoDTO(s.slug(), s.name(), s.rainAlert(), s.rainReason(), text(s.rainReason()), s.pointFlood(),
                s.floodReason(), text(s.floodReason()), s.rainSince(), s.trigger(), s.probForte1h(), s.probOn(), s.probOff(),
                s.rainTeMm(), s.capacityTeMm(), s.rain30minMm(), cm(s.hNowMm()), cm(s.hProjQ50MaxMm()), cm(s.hProjQ80MaxMm()),
                s.floodInMinutes(), json(s.projectionJson(), new TypeReference<Map<String, Object>>() {}), s.q50_1h(), s.q80_1h(),
                s.rainModelVersion(), s.bucketVersion(), s.trYears(), s.teMinutes(),
                json(s.sourcesJson(), new TypeReference<List<String>>() {}), s.latestReadingUtc(), s.latestReadingAgeMin(),
                s.eventId() == null ? null : s.eventId().toString(), s.shadow(), s.updatedAt(),
                AlertaPontoDTO.PHYSICAL_MODEL_LABEL, AlertaPontoDTO.ATTRIBUTION);
    }

    private <T> T json(String s, TypeReference<T> t) {
        try {
            return s == null ? null : mapper.readValue(s, t);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<AlertaTransicaoDTO> transitions(String slug, int hours, int limit) {
        Long id = null;
        if (slug != null && !slug.isBlank()) {
            id = points.findBySlug(slug).orElseThrow(() -> new ResourceNotFoundException("Ponto não encontrado: " + slug)).getId();
        }
        Instant to = clock.instant();
        return repo.transitions(id, to.minus(Duration.ofHours(hours)), to, limit).stream()
                .map(t -> new AlertaTransicaoDTO(t.id(), t.slug(), t.at(), t.field(), t.fromState(), t.toState(), t.kind(),
                        t.reason(), t.eventId() == null ? null : t.eventId().toString(), t.probForte1h(), t.rainTeMm(),
                        cm(t.hNowMm()), cm(t.hProjQ50MaxMm()), t.floodInMinutes(), t.shadow())).toList();
    }

    @Override
    public AlertaLatenciaDTO latency(int days) {
        Instant to = clock.instant();
        Instant from = to.minus(Duration.ofDays(days));
        List<AlertaLatenciaDTO.Segment> segs = repo.latency(from, to).stream()
                .map(l -> new AlertaLatenciaDTO.Segment(l.segment(), l.wetHour(), l.n(), l.medianS(), l.p90S(), l.maxS())).toList();
        double budget = props.getLatency().getBudgetP90Minutes() * 60.0;
        Boolean wet = segs.stream().filter(s -> s.segment().equals("total") && s.wetHour()).findFirst()
                .map(s -> s.p90S() != null && s.p90S() < budget).orElse(null);
        Boolean dry = segs.stream().filter(s -> s.segment().equals("total") && !s.wetHour()).findFirst()
                .map(s -> s.p90S() != null && s.p90S() < budget).orElse(null);
        String note = wet == null ? "Sem hora com chuva no período: o orçamento vale para hora com chuva e ainda não foi medido."
                : null;
        return new AlertaLatenciaDTO(from, to, props.getLatency().getBudgetP90Minutes(), segs, wet, dry, note);
    }
}
