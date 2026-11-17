package com.projeto.mapi.service.alerta.impl;

import com.projeto.mapi.service.alerta.PointRain10MinComposer;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertaConferenciaServiceImplTest {

    static final Instant DAY = Instant.parse("2026-11-20T00:00:00Z");

    static Instant at(int min) {
        return DAY.plusSeconds(min * 60L);
    }

    @Test
    void cruzamentoDe25mmEm60MinSemOutroNas6hAnteriores() {
        List<PointRain10MinComposer.Slot> s = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            double mm = (i >= 10 && i <= 15) ? 5.0 : 0.0;          // 30 mm de 01:40 a 02:30
            s.add(new PointRain10MinComposer.Slot(at(i * 10), mm, "A", 1, false, 10, null));
        }
        List<Instant> c = AlertaConferenciaServiceImpl.crossings(s, DAY);
        assertThat(c).containsExactly(at(150));                      // 25 mm no passo das 02:30
    }

    @Test
    void avisoAntesDoCruzamentoContaAntecedenciaEEpisodioSemCruzamentoEFalso() {
        List<AlertaConferenciaServiceImpl.Cycle> cy = new ArrayList<>();
        for (int m = 0; m < 600; m += 3) {
            String rain = (m >= 120 && m < 160) || (m >= 400 && m < 430) ? "ATENCAO" : m < 30 ? "SEM_DADO" : "NENHUM";
            cy.add(new AlertaConferenciaServiceImpl.Cycle(at(m), rain, "NAO", 0.0, 0.0));
        }
        AlertaConferenciaServiceImpl.Row r = AlertaConferenciaServiceImpl.evaluate(List.of(at(150)), cy, DAY, at(1440));
        assertThat(r.events).isEqualTo(1);
        assertThat(r.warned).isEqualTo(1);
        assertThat(r.leads).containsExactly(30.0);
        assertThat(r.episodes).isEqualTo(2);
        assertThat(r.falseEp).isEqualTo(1);
        assertThat(r.semDado).isEqualTo(30.0);
    }
}
