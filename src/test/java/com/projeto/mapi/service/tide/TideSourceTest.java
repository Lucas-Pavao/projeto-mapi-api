package com.projeto.mapi.service.tide;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TideSourceTest {

    private record Row(Instant ts, String source) {}

    private static final Instant T0 = Instant.parse("2026-11-17T00:00:00Z");

    @Test
    void mapeiaFonteDaObservacao() {
        assertThat(TideSource.fromObservationSource("TIDE_HARMONIC_F41")).isEqualTo(TideSource.HARMONIC);
        assertThat(TideSource.fromObservationSource("TABUAMARE_PE02")).isEqualTo(TideSource.OFFICIAL);
        assertThat(TideSource.fromObservationSource("DHN_TABUA_PE02")).isEqualTo(TideSource.OFFICIAL);
        assertThat(TideSource.fromObservationSource(null)).isNull();
    }

    @Test
    void interpoladoEntreOficialEHarmonicoEHarmonico() {
        List<Row> rows = List.of(new Row(T0, "TABUAMARE_PE02"), new Row(T0.plusSeconds(6 * 3600), "TIDE_HARMONIC_F41"));
        assertThat(TideSource.at(rows, T0.plusSeconds(3600), Row::ts, Row::source)).isEqualTo(TideSource.HARMONIC);
    }

    @Test
    void doisOficiaisDaoOficialEHarmonicoLongeDoIntervaloNaoContamina() {
        List<Row> rows = List.of(new Row(T0.minusSeconds(6 * 3600), "TIDE_HARMONIC_F41"), new Row(T0, "TABUAMARE_PE02"),
                new Row(T0.plusSeconds(6 * 3600), "DHN_TABUA_PE02"));
        assertThat(TideSource.at(rows, T0.plusSeconds(3600), Row::ts, Row::source)).isEqualTo(TideSource.OFFICIAL);
    }

    @Test
    void semExtremosDaNull() {
        assertThat(TideSource.at(List.<Row>of(), T0, Row::ts, Row::source)).isNull();
    }
}
