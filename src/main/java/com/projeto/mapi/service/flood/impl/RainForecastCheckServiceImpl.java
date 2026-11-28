package com.projeto.mapi.service.flood.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.mapper.AlertMapper;
import com.projeto.mapi.model.Alert;
import com.projeto.mapi.model.AlertKind;
import com.projeto.mapi.repository.AlertRepository;
import com.projeto.mapi.repository.analise.RainForecastCheckRepository;
import com.projeto.mapi.repository.analise.RainForecastCheckRepository.SeriesBreak;
import com.projeto.mapi.service.alert.AlertCreatedEvent;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Reference;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Result;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Row;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Rule;
import com.projeto.mapi.service.flood.RainForecastCheckService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Conferência semanal do rain_v1 (onda 16, Fase 1.1). Toda segunda, confere a semana anterior e o acumulado de cada trecho
 * (antes e depois de cada quebra de série que afeta o rain_v1, V43). Se o acumulado do trecho piorar além do intervalo do
 * teste (IC do trecho inteiro abaixo do IC do teste), abre pendência (alerta MODEL_CHECK, um por episódio e prazo).
 * Regra: docs/PRE-REGISTRO-CONFERENCIA-SEMANAL-RAIN.md.
 */
@Service
@Slf4j
public class RainForecastCheckServiceImpl implements RainForecastCheckService {

    static final ZoneId RECIFE = ZoneId.of("America/Recife");
    static final String AFFECTS = "rain_v1";

    private final RainForecastCheckRepository repo;
    private final AlertRepository alerts;
    private final AlertMapper alertMapper;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final Reference reference;
    private final Rule rule;
    private final boolean enabled;

    public RainForecastCheckServiceImpl(RainForecastCheckRepository repo, AlertRepository alerts, AlertMapper alertMapper,
                                        ApplicationEventPublisher publisher, Clock clock, ObjectMapper mapper,
                                        @Value("${mapi.rain-forecast.check.enabled:true}") boolean enabled) {
        this.repo = repo;
        this.alerts = alerts;
        this.alertMapper = alertMapper;
        this.publisher = publisher;
        this.clock = clock;
        this.reference = Reference.load(mapper);
        this.rule = Rule.defaults();
        this.enabled = enabled;
    }

    @Scheduled(cron = "${mapi.rain-forecast.check-cron:0 0 6 * * MON}", zone = "America/Recife")
    public void weekly() {
        if (!enabled) return;
        LocalDate thisMonday = LocalDate.now(clock.withZone(RECIFE)).with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        try {
            int n = runWeek(thisMonday.minusWeeks(1));
            log.info("Conferência semanal do rain_v1: {} linha(s)", n);
        } catch (RuntimeException e) {
            log.warn("Conferência semanal do rain_v1 falhou: {}", e.toString());
        }
    }

    @Override
    public int runWeek(LocalDate monday) {
        Instant from = monday.atStartOfDay(RECIFE).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(RECIFE).toInstant();
        List<SeriesBreak> breaks = repo.breaksAffecting(AFFECTS);
        int written = 0;
        // 1. a semana, cortada em cada quebra que cai dentro dela
        for (Segment s : segments(from, to, breaks)) {
            for (Result r : RainForecastCheckCalculator.compute(repo.rows(s.from, s.to), reference, rule)) {
                repo.upsert("SEMANA", s.from, s.to, s.label, r, RainForecastCheckCalculator.RULE_VERSION);
                written++;
            }
        }
        // 2. o acumulado de cada trecho até o fim da semana: é o que decide a pendência
        Instant first = repo.firstIssued();
        if (first == null) return written;
        for (Segment s : segments(first, to, breaks)) {
            for (Result r : RainForecastCheckCalculator.compute(repo.rows(s.from, s.to), reference, rule)) {
                repo.upsert("TRECHO", s.from, s.to, s.label, r, RainForecastCheckCalculator.RULE_VERSION);
                written++;
                if ("PIORA".equals(r.status())) openPending(s, r);
            }
        }
        return written;
    }

    record Segment(Instant from, Instant to, String label) {}

    /** Corta [from, to) em cada quebra; o rótulo diz de que lado da quebra mais próxima o trecho fica. */
    static List<Segment> segments(Instant from, Instant to, List<SeriesBreak> breaks) {
        List<Instant> cuts = new ArrayList<>();
        for (SeriesBreak b : breaks) if (b.breakAt().isAfter(from) && b.breakAt().isBefore(to)) cuts.add(b.breakAt());
        List<Segment> out = new ArrayList<>();
        Instant a = from;
        for (Instant c : cuts) {
            out.add(new Segment(a, c, label(a, breaks)));
            a = c;
        }
        out.add(new Segment(a, to, label(a, breaks)));
        return out;
    }

    static String label(Instant segStart, List<SeriesBreak> breaks) {
        SeriesBreak last = null;
        SeriesBreak next = null;
        for (SeriesBreak b : breaks) {
            if (!b.breakAt().isAfter(segStart)) last = b;
            else if (next == null) next = b;
        }
        if (last != null) return "depois de " + last.breakAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        if (next != null) return "antes de " + next.breakAt().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        return "sem quebra";
    }

    private void openPending(Segment s, Result r) {
        Instant now = clock.instant();
        String key = "rain_v1:" + r.horizonHours() + "h:" + s.label;
        if (repo.alertedSince(key, now.minus(Duration.ofDays(7)))) return;
        Alert a = alerts.save(Alert.builder()
                .kind(AlertKind.MODEL_CHECK)
                .severity(Alert.Severity.WARNING)
                .stationId(key)
                .title(String.format(Locale.ROOT, "Previsão de chuva pior que no teste (%d h, %s)", r.horizonHours(), s.label))
                .detail(String.format(new Locale("pt", "BR"),
                        "Ganho sobre a persistência %.3f (IC %.3f a %.3f) contra %.3f (IC %.3f a %.3f) no teste; %d previsões em %d dias.",
                        r.skill(), r.skillLo(), r.skillHi(), r.testSkill(), r.testSkillLo(), r.testSkillHi(), r.nForecasts(), r.nDays()))
                .observedValue(r.skill())
                .triggeredAt(now).build());
        publisher.publishEvent(new AlertCreatedEvent(alertMapper.toDto(a)));
        log.warn("Pendência aberta: {}", a.getTitle());
    }

    @Override
    public List<Map<String, Object>> latest(int limit) {
        return repo.latest(limit);
    }
}
