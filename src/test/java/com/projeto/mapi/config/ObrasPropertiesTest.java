package com.projeto.mapi.config;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Período da obra por ponto (onda 16, Fase 3.4): a validação nunca mistura antes e depois. */
class ObrasPropertiesTest {

    @Test
    void semDataDeOperacaoTudoEAntesESemObraNosOutros() {
        ObrasProperties o = new ObrasProperties();
        assertThat(o.periodo("MASCARENHAS_IMBIRIBEIRA", Instant.parse("2027-04-01T12:00:00Z"))).isEqualTo("ANTES");
        assertThat(o.periodo("CIN_UFPE", Instant.parse("2027-04-01T12:00:00Z"))).isEqualTo("SEM_OBRA");
    }

    @Test
    void comDataDeOperacaoODiaLocalDecide() {
        ObrasProperties o = new ObrasProperties();
        o.getPontos().get("MASCARENHAS_IMBIRIBEIRA").get(0).setInicioOperacao(LocalDate.of(2027, 3, 1));
        // 01/03 00h30 UTC ainda é 28/02 no Recife
        assertThat(o.periodo("MASCARENHAS_IMBIRIBEIRA", Instant.parse("2027-03-01T00:30:00Z"))).isEqualTo("ANTES");
        assertThat(o.periodo("MASCARENHAS_IMBIRIBEIRA", Instant.parse("2027-03-01T03:00:00Z"))).isEqualTo("DEPOIS");
    }
}
