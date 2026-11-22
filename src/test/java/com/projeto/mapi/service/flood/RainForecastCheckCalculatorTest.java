package com.projeto.mapi.service.flood;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.repository.analise.RainForecastCheckRepository.SeriesBreak;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Reference;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Result;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Row;
import com.projeto.mapi.service.flood.RainForecastCheckCalculator.Rule;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Conta da conferência semanal: faixas da persistência, pinball, ganho, status e cortes nas quebras de série. */
class RainForecastCheckCalculatorTest {

    private final Reference ref = Reference.load(new ObjectMapper());

    @Test
    void referenciaDoTesteBateComORelatorioDoRainV1() {
        assertThat(ref.testSkill().get(1)[0]).isCloseTo(0.165, within(0.002));
        assertThat(ref.testSkill().get(6)[1]).isLessThan(ref.testSkill().get(6)[0]);
        assertThat(ref.persistence().get(1)).containsKeys("seco", "0-1", "1-5", ">=5", "sem_dado");
    }

    @Test
    void faixasDoTreinoEAusenteNaoViraSeco() {
        assertThat(RainForecastCheckCalculator.band(null)).isEqualTo("sem_dado");
        assertThat(RainForecastCheckCalculator.band(0.0)).isEqualTo("seco");
        assertThat(RainForecastCheckCalculator.band(1.0)).isEqualTo("0-1");
        assertThat(RainForecastCheckCalculator.band(5.0)).isEqualTo("1-5");
        assertThat(RainForecastCheckCalculator.band(5.1)).isEqualTo(">=5");
    }

    @Test
    void pinballDosTresQuantis() {
        // y = 2; q = 1, 2, 4 -> 0,5·1 = 0,5; 0; 0,05·2 = 0,1 -> média 0,2
        assertThat(RainForecastCheckCalculator.pinball(2, 1, 2, 4)).isCloseTo(0.2, within(1e-12));
    }

    private static List<Row> week(double modelError, int days, int perDay, double rainMm) {
        List<Row> rows = new ArrayList<>();
        Instant t0 = Instant.parse("2026-12-07T12:00:00Z");
        for (int d = 0; d < days; d++) {
            for (int i = 0; i < perDay; i++) {
                double y = (i % 3 == 0) ? rainMm : 0.0;
                Double p60 = (i % 3 == 0) ? 2.0 : 0.0;
                double q = y + modelError;
                rows.add(new Row(t0.plus(Duration.ofDays(d)).plus(Duration.ofMinutes(10L * i)), 1, q, q + 0.5, q + 2, y, p60));
            }
        }
        return rows;
    }

    @Test
    void modeloBomFicaOkEModeloRuimViraPiora() {
        Result good = RainForecastCheckCalculator.compute(week(0.0, 7, 60, 3.0), ref, Rule.defaults()).get(0);
        assertThat(good.status()).isEqualTo("OK");
        assertThat(good.skill()).isGreaterThan(good.testSkillLo());
        Result bad = RainForecastCheckCalculator.compute(week(4.0, 7, 60, 3.0), ref, Rule.defaults()).get(0);
        assertThat(bad.status()).isEqualTo("PIORA");
        assertThat(bad.skillHi()).isLessThan(bad.testSkillLo());
    }

    @Test
    void semanaSecaOuCurtaViraPoucoDadoNuncaPiora() {
        assertThat(RainForecastCheckCalculator.compute(week(4.0, 7, 60, 0.0), ref, Rule.defaults()).get(0).status())
                .isEqualTo("POUCO_DADO");
        assertThat(RainForecastCheckCalculator.compute(week(4.0, 2, 60, 3.0), ref, Rule.defaults()).get(0).status())
                .isEqualTo("POUCO_DADO");
    }

    @Test
    void bootstrapDeterministico() {
        List<Row> rows = week(1.0, 7, 60, 3.0);
        assertThat(RainForecastCheckCalculator.compute(rows, ref, Rule.defaults()))
                .isEqualTo(RainForecastCheckCalculator.compute(rows, ref, Rule.defaults()));
    }

    @Test
    void semanaQueCruzaAQuebraViraDoisTrechos() {
        List<SeriesBreak> breaks = List.of(new SeriesBreak(Instant.parse("2026-11-30T00:00:00Z"), "best_match", "x"));
        var segs = com.projeto.mapi.service.flood.impl.RainForecastCheckServiceImplAccess.segments(
                Instant.parse("2026-11-23T03:00:00Z"), Instant.parse("2026-12-07T03:00:00Z"), breaks);
        assertThat(segs).hasSize(2);
        assertThat(segs.get(0)).contains("antes de 2026-11-30");
        assertThat(segs.get(1)).contains("depois de 2026-11-30");
    }
}
