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

    @Test
    void provenienciaDistinguePrimariaEstendidaEReanalise() {
        var primaria = PointRainComposer.classifyProvenance(10.0, 2900.0, 3000.0, 5000.0, false, 0.85, 0.60);
        assertThat(primaria.tier()).isEqualTo(PointRainComposer.RainTier.MEASURED_PRIMARY);
        assertThat(primaria.confidence()).isEqualTo(1.0);
        assertThat(primaria.flag()).isNull();

        var estendida = PointRainComposer.classifyProvenance(10.0, 4200.0, 3000.0, 5000.0, false, 0.85, 0.60);
        assertThat(estendida.tier()).isEqualTo(PointRainComposer.RainTier.MEASURED_EXTENDED);
        assertThat(estendida.confidence()).isEqualTo(0.85);
        assertThat(estendida.flag()).isEqualTo("DISTANT_STATION");

        var reanalise = PointRainComposer.classifyProvenance(null, null, 3000.0, 5000.0, true, 0.85, 0.60);
        assertThat(reanalise.tier()).isEqualTo(PointRainComposer.RainTier.REANALYSIS_FALLBACK);
        assertThat(reanalise.confidence()).isEqualTo(0.60);
        assertThat(reanalise.flag()).isEqualTo("SYNTHETIC_RAIN_FALLBACK");

        assertThat(PointRainComposer.classifyProvenance(null, null, 3000.0, 5000.0, false, 0.85, 0.60)).isNull();
    }
}
