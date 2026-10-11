package com.projeto.mapi.service.weather;

import com.projeto.mapi.dto.HourlyPrecipitationDTO;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecentRainAccumulatorTest {

    private static HourlyPrecipitationDTO horas(String inicio, Double... valores) {
        LocalDateTime t = LocalDateTime.parse(inicio);
        List<String> times = new ArrayList<>();
        for (int i = 0; i < valores.length; i++) times.add(t.plusHours(i).toString());
        return new HourlyPrecipitationDTO(times, Arrays.asList(valores));
    }

    @Test
    void somaAsUltimasHorasFechadasAteAgora() {
        // 10:00..14:00; agora 13:45 -> a hora rotulada 14:00 ainda não fechou e fica fora.
        var h = horas("2026-10-11T10:00", 1.0, 2.0, 3.0, 4.0, 50.0);
        LocalDateTime agora = LocalDateTime.parse("2026-10-11T13:45");
        assertThat(RecentRainAccumulator.sumLastHours(h, agora, 3)).isEqualTo(9.0);
        assertThat(RecentRainAccumulator.sumLastHours(h, agora, 4)).isEqualTo(10.0);
    }

    @Test
    void janelaIncompletaOuComBuracoViraNulo() {
        var h = horas("2026-10-11T10:00", 1.0, null, 3.0);
        LocalDateTime agora = LocalDateTime.parse("2026-10-11T12:30");
        assertThat(RecentRainAccumulator.sumLastHours(h, agora, 3)).isNull();
        assertThat(RecentRainAccumulator.sumLastHours(h, agora, 5)).isNull();
        assertThat(RecentRainAccumulator.sumLastHours(null, agora, 3)).isNull();
    }
}
