package com.projeto.mapi.service.export.label;

import com.projeto.mapi.service.export.label.OccurrenceLabeler.Occ;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OccurrenceLabelerTest {

    private static final List<Integer> H = List.of(6, 3);

    private static Occ hour(long id, String iso) {
        Instant i = Instant.parse(iso);
        return new Occ(id, i, i.plusNanos(1000), true);
    }

    @Test
    void positivoSoQuandoOcorrenciaHourEstaEmTMaisHAberto() {
        var occs = List.of(hour(7, "2026-05-08T17:30:00Z"));
        assertThat(OccurrenceLabeler.label(occs, Instant.parse("2026-05-08T11:00:00Z"), H, h -> h).byHorizon().get(6)).isZero();
        var l = OccurrenceLabeler.label(occs, Instant.parse("2026-05-08T12:00:00Z"), H, h -> h);
        assertThat(l.byHorizon().get(6)).isEqualTo(1);
        assertThat(l.byHorizon().get(3)).isZero();
        assertThat(l.occurrenceIds()).isEqualTo("7");
        assertThat(OccurrenceLabeler.label(occs, Instant.parse("2026-05-08T17:00:00Z"), H, h -> h).byHorizon().get(3)).isEqualTo(1);
    }

    @Test
    void janelaPosEventoExcluiEOutroGEhConfiguravel() {
        var occs = List.of(hour(7, "2026-05-08T17:30:00Z"));
        var t = Instant.parse("2026-05-08T20:00:00Z"); // evento a 2h30 atrás
        var l = OccurrenceLabeler.label(occs, t, H, h -> h);
        assertThat(l.byHorizon().get(6)).isNull();
        assertThat(l.byHorizon().get(3)).isNull();
        assertThat(l.excludedReason()).isEqualTo("POST_EVENT_WINDOW");
        var g1 = OccurrenceLabeler.label(occs, t, H, h -> 1);
        assertThat(g1.byHorizon().get(6)).isZero();
        assertThat(g1.excludedReason()).isNull();
    }

    @Test
    void ocorrenciaDayExcluiETemPrecedenciaENuncaVira0() {
        var day = new Occ(9, Instant.parse("2026-05-08T03:00:00Z"), Instant.parse("2026-05-09T03:00:00Z"), false);
        var l = OccurrenceLabeler.label(List.of(day), Instant.parse("2026-05-08T12:00:00Z"), H, h -> h);
        assertThat(l.byHorizon().values()).containsOnlyNulls();
        assertThat(l.excludedReason()).isEqualTo("DAY_EVENT_SAME_DAY");
        assertThat(l.day()).isEqualTo(1);
        // 6 h antes do dia: h6 já toca o dia, h3 ainda não
        var antes = OccurrenceLabeler.label(List.of(day), Instant.parse("2026-05-07T22:00:00Z"), H, h -> h);
        assertThat(antes.byHorizon().get(6)).isNull();
        assertThat(antes.byHorizon().get(3)).isZero();
        // dia civil é o de America/Recife do instante t
        assertThat(OccurrenceLabeler.label(List.of(day), Instant.parse("2026-05-08T02:00:00Z"), H, h -> h).day()).isZero();
    }
}
