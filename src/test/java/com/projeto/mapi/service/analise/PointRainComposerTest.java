package com.projeto.mapi.service.analise;

import com.projeto.mapi.service.analise.PointRainComposer.Candidate;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Seleção do maior valor entre as estações válidas (ADR 0022). */
class PointRainComposerTest {

    @Test
    void maiorEntreValidasIgnorandoReprovada() {
        var h = PointRainComposer.compose(List.of(
                new Candidate("PERTO", 700, true, 12.0, 6, 60),
                new Candidate("REPROVADA", 900, false, 80.0, 6, 60),
                new Candidate("LONGE", 3000, true, 30.5, 4, 50)));
        assertThat(h.value()).isEqualTo(30.5);
        assertThat(h.stationId()).isEqualTo("LONGE");
        assertThat(h.distanceM()).isEqualTo(3000);
        assertThat(h.nCandidates()).isEqualTo(3);
        assertThat(h.nValid()).isEqualTo(2);
        assertThat(h.nValidWithValue()).isEqualTo(2);
        assertThat(h.nBlocks()).isEqualTo(4);
        assertThat(h.coveredMin()).isEqualTo(50);
    }

    @Test
    void empateFicaComAMaisProxima() {
        var h = PointRainComposer.compose(List.of(new Candidate("B", 2000, true, 0.0, 1, 60),
                new Candidate("A", 1000, true, 0.0, 1, 60)));
        assertThat(h.stationId()).isEqualTo("A");
        assertThat(h.value()).isZero();
    }

    @Test
    void semValidaComValorEhLacunaNuncaZero() {
        var h = PointRainComposer.compose(List.of(new Candidate("A", 1000, true, Double.NaN, 0, Double.NaN),
                new Candidate("B", 2000, false, 5.0, 1, 60)));
        assertThat(h.value()).isNull();
        assertThat(h.stationId()).isNull();
        assertThat(h.nValid()).isEqualTo(1);
        assertThat(h.nValidWithValue()).isZero();
    }
}
