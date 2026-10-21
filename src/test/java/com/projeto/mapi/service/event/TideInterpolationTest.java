package com.projeto.mapi.service.event;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TideInterpolationTest {

    private static final Instant T0 = Instant.parse("2026-10-21T00:00:00Z");

    @Test
    void cossenoEntreExtremos_nasPontasEnoMeio() {
        var ex = List.of(new TideInterpolation.Extreme(T0, 0.2), new TideInterpolation.Extreme(T0.plus(Duration.ofHours(6)), 2.2));
        var s = TideInterpolation.sample(ex, T0, T0.plus(Duration.ofHours(6)), Duration.ofHours(3));
        assertThat(s).hasSize(2);
        assertThat(s.get(0).v()).isEqualTo(0.2);
        assertThat(s.get(1).v()).isEqualTo(1.2); // meio do caminho: média dos extremos
    }

    @Test
    void foraDosExtremosOuComLacunaGrandeFicaNulo() {
        var ex = List.of(new TideInterpolation.Extreme(T0, 0.2), new TideInterpolation.Extreme(T0.plus(Duration.ofHours(10)), 2.2));
        var s = TideInterpolation.sample(ex, T0.minus(Duration.ofHours(1)), T0.plus(Duration.ofHours(2)), Duration.ofHours(1));
        assertThat(s).allSatisfy(p -> assertThat(p.v()).isNull());
        assertThat(TideInterpolation.sample(List.of(), T0, T0.plus(Duration.ofHours(1)), Duration.ofMinutes(30)))
                .allSatisfy(p -> assertThat(p.v()).isNull());
    }
}
