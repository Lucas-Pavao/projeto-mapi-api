package com.projeto.mapi.service.alagamento;

import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator.Cycle;
import com.projeto.mapi.service.alagamento.ShadowWeeklyReportCalculator.Result;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Relatório semanal do modo sombra (Fase 9.4): ciclos, buracos, SEM_DADO por motivo e episódios de 3 h. */
class ShadowWeeklyReportCalculatorTest {

    private static final Instant FROM = Instant.parse("2026-11-23T03:00:00Z");   // segunda 00h de Recife
    private static final Instant TO = FROM.plus(Duration.ofDays(7));
    private static final Map<Long, String> POINTS = Map.of(1L, "CIN_UFPE", 2L, "MASCARENHAS_IMBIRIBEIRA");

    private static List<Cycle> every10(long point, Instant a, Instant b, String level, String reason) {
        List<Cycle> out = new ArrayList<>();
        for (Instant t = a; t.isBefore(b); t = t.plus(Duration.ofMinutes(10))) out.add(new Cycle(point, t, level, reason));
        return out;
    }

    @Test
    void semanaCheiaSemBuraco() {
        Result r = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, every10(1L, FROM, TO, "SEM_ALERTA", null)).get(0);
        assertThat(r.nExpected()).isEqualTo(1008);
        assertThat(r.nCycles()).isEqualTo(1008);
        assertThat(r.longestGapMin()).isEqualTo(10);
        assertThat(r.gapsOver30Min()).isZero();
        assertThat(r.byLevel()).containsEntry("SEM_ALERTA", 1008).containsEntry("ALERTA", 0);
    }

    @Test
    void pilhaParadaApareceComoBuracoENaoComoSemAlerta() {
        List<Cycle> cs = new ArrayList<>(every10(1L, FROM, FROM.plus(Duration.ofDays(1)), "SEM_ALERTA", null));
        cs.addAll(every10(1L, FROM.plus(Duration.ofDays(5)), TO, "SEM_ALERTA", null));
        Result r = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, cs).get(0);
        assertThat(r.longestGapMin()).isEqualTo(4 * 24 * 60 + 10);
        assertThat(r.gapsOver30Min()).isEqualTo(1);
        assertThat(r.nCycles()).isEqualTo(3 * 144);
    }

    @Test
    void pontoSemNenhumCicloSaiComASemanaInteiraDeBuraco() {
        List<Result> rs = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, every10(1L, FROM, TO, "SEM_ALERTA", null));
        Result m = rs.get(1);
        assertThat(m.slug()).isEqualTo("MASCARENHAS_IMBIRIBEIRA");
        assertThat(m.nCycles()).isZero();
        assertThat(m.longestGapMin()).isEqualTo(7 * 24 * 60);
    }

    @Test
    void semDadoPorMotivoEMotivoAusente() {
        List<Cycle> cs = List.of(new Cycle(1L, FROM, "SEM_DADO", "CHUVA_AUSENTE"), new Cycle(1L, FROM.plusSeconds(600), "SEM_DADO", null),
                new Cycle(1L, FROM.plusSeconds(1200), "BAIXO", null));
        Result r = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, cs).get(0);
        assertThat(r.byLevel()).containsEntry("SEM_DADO", 3);
        assertThat(r.semDadoByReason()).containsEntry("CHUVA_AUSENTE", 1).containsEntry("SEM_MOTIVO", 2);
    }

    @Test
    void episodiosSeparadosPorTresHorasSemAlerta() {
        List<Cycle> cs = new ArrayList<>();
        cs.addAll(every10(2L, FROM.plus(Duration.ofHours(10)), FROM.plus(Duration.ofHours(11)), "ALERTA", null));
        cs.add(new Cycle(2L, FROM.plus(Duration.ofHours(12)), "ATENCAO", null));
        cs.addAll(every10(2L, FROM.plus(Duration.ofHours(14)), FROM.plus(Duration.ofHours(15)), "ALERTA", null));
        cs.addAll(every10(2L, FROM.plus(Duration.ofHours(20)), FROM.plus(Duration.ofHours(21)), "ATENCAO", null));
        Result r = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, cs).get(1);
        // só ALERTA: de 10h50 a 14h00 são 3h10 sem ALERTA (a ATENÇÃO das 12h não conta) -> 2 episódios
        assertThat(r.alertaEpisodes()).isEqualTo(2);
        // ALERTA ou ATENÇÃO: 10h50 -> 12h00 -> 14h00 ficam juntos; 14h50 -> 20h00 (5h10) separa -> 2 episódios
        assertThat(r.alertaOuAtencaoEpisodes()).isEqualTo(2);
    }

    @Test
    void cicloForaDaJanelaNaoConta() {
        List<Cycle> cs = List.of(new Cycle(1L, FROM.minusSeconds(1), "ALERTA", null), new Cycle(1L, TO, "ALERTA", null));
        Result r = ShadowWeeklyReportCalculator.compute(FROM, TO, POINTS, cs).get(0);
        assertThat(r.nCycles()).isZero();
        assertThat(r.alertaEpisodes()).isZero();
    }
}
