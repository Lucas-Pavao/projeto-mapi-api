package com.projeto.mapi.service.alagamento.impl;

import com.projeto.mapi.repository.analise.ShadowWeeklyReportRepository;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator.Result;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/**
 * Relatório semanal do modo sombra (onda 16, Fase 9.4). Toda segunda, grava a semana anterior: por ponto, ciclos feitos
 * contra esperados, buracos sem ciclo, ciclos por nível, SEM_DADO por motivo e episódios de alerta. Regra em
 * docs/PRE-REGISTRO-VALIDACAO-2027.md, seção 9.
 */
@Service
@Slf4j
public class ShadowWeeklyReportServiceImpl implements ShadowWeeklyReportService {

    static final ZoneId RECIFE = ZoneId.of("America/Recife");

    private final ShadowWeeklyReportRepository repo;
    private final Clock clock;
    private final boolean enabled;

    public ShadowWeeklyReportServiceImpl(ShadowWeeklyReportRepository repo, Clock clock,
                                         @Value("${mapi.alagamento.relatorio-semanal.enabled:true}") boolean enabled) {
        this.repo = repo;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Scheduled(cron = "${mapi.alagamento.relatorio-semanal.cron:0 15 6 * * MON}", zone = "America/Recife")
    public void weekly() {
        if (!enabled) return;
        LocalDate thisMonday = LocalDate.now(clock.withZone(RECIFE)).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        try {
            log.info("Relatório semanal do modo sombra: {} linha(s)", runWeek(thisMonday.minusWeeks(1)));
        } catch (RuntimeException e) {
            log.warn("Relatório semanal do modo sombra falhou: {}", e.toString());
        }
    }

    @Override
    public int runWeek(LocalDate monday) {
        Instant from = monday.atStartOfDay(RECIFE).toInstant();
        Instant to = monday.plusWeeks(1).atStartOfDay(RECIFE).toInstant();
        Instant now = clock.instant();
        if (to.isAfter(now)) to = now;                 // semana em curso: até agora (os esperados também)
        List<Result> rows = ShadowWeeklyReportCalculator.compute(from, to, repo.activePoints(), repo.cycles(from, to));
        for (Result r : rows) repo.upsert(from, to, r, ShadowWeeklyReportCalculator.RULE_VERSION);
        return rows.size();
    }

    @Override
    public List<Map<String, Object>> latest(int weeks) {
        return repo.latestWeeks(weeks);
    }
}
