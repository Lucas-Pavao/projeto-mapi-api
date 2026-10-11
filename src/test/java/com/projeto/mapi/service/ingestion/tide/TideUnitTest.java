package com.projeto.mapi.service.ingestion.tide;

import com.projeto.mapi.service.export.TideFeatureCalculator;
import com.projeto.mapi.service.ingestion.adapter.tide.CosineTideInterpolator;
import com.projeto.mapi.service.ingestion.adapter.tide.CosineTideInterpolator.Point;
import com.projeto.mapi.service.ingestion.adapter.tide.TideExtreme;
import com.projeto.mapi.service.ingestion.adapter.tide.TideExtremeClassifier;
import com.projeto.mapi.service.ingestion.validation.TimestampNormalizer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/** Partes puras da maré prevista: interpolação por cosseno, classificação, fuso UTC-3 e features. */
class TideUnitTest {

    private static final Instant T0 = Instant.parse("2023-01-01T03:00:00Z");

    private static Point p(Instant ts, double level) {
        return new Point(ts, level, "TEST");
    }

    /** Resposta conhecida: baixa 0,0 m em t0 e preamar 2,0 m seis horas depois. cos(pi/6)=sqrt(3)/2, cos(pi/3)=1/2. */
    @Test
    void cossenoComRespostaConhecida() {
        Point low = p(T0, 0.0);
        Point high = p(T0.plus(Duration.ofHours(6)), 2.0);
        assertThat(CosineTideInterpolator.between(low, high, T0)).isCloseTo(0.0, within(1e-12));
        assertThat(CosineTideInterpolator.between(low, high, T0.plus(Duration.ofHours(1))))
                .isCloseTo(1.0 - Math.sqrt(3) / 2.0, within(1e-12));              // 0,1339746
        assertThat(CosineTideInterpolator.between(low, high, T0.plus(Duration.ofHours(2)))).isCloseTo(0.5, within(1e-12));
        assertThat(CosineTideInterpolator.between(low, high, T0.plus(Duration.ofHours(3)))).isCloseTo(1.0, within(1e-12));
        assertThat(CosineTideInterpolator.between(low, high, T0.plus(Duration.ofHours(6)))).isCloseTo(2.0, within(1e-12));
    }

    /** Extremo em hora cheia vale o próprio valor; horas fora da cobertura ou em lacuna ficam sem valor. */
    @Test
    void horasCobertasExtremoExatoELacuna() {
        List<Point> s = List.of(p(T0, 0.0), p(T0.plus(Duration.ofHours(6)), 2.0),
                p(T0.plus(Duration.ofHours(20)), 0.2)); // 14 h entre o 2º e o 3º: acima do limite de 8 h
        var h = CosineTideInterpolator.hourly(s, T0.minus(Duration.ofHours(2)), T0.plus(Duration.ofHours(24)), Duration.ofHours(8));
        // antes de T0: nada; T0..T0+6 h: 7 horas (inclui os dois extremos); depois: lacuna de 14 h => nada;
        // T0+20 h é extremo exato (valor próprio); depois do último: nada
        assertThat(h).extracting(CosineTideInterpolator.Hourly::hour).first().isEqualTo(T0);
        assertThat(h).hasSize(8);
        assertThat(h.get(0).level()).isEqualTo(0.0);
        assertThat(h.get(6).hour()).isEqualTo(T0.plus(Duration.ofHours(6)));
        assertThat(h.get(6).level()).isEqualTo(2.0);
        assertThat(h.get(7).hour()).isEqualTo(T0.plus(Duration.ofHours(20)));
        assertThat(h.get(7).level()).isEqualTo(0.2);
    }

    /** T6 (borda da hora): a janela é [from, to); só horas cheias, sem a hora de {@code to}. */
    @Test
    void bordaDaHoraJanelaSemiaberta() {
        List<Point> s = List.of(p(T0, 0.0), p(T0.plus(Duration.ofHours(6)), 2.0));
        var h = CosineTideInterpolator.hourly(s, T0.plusSeconds(1), T0.plus(Duration.ofHours(3)), Duration.ofHours(8));
        assertThat(h).extracting(CosineTideInterpolator.Hourly::hour)
                .containsExactly(T0.plus(Duration.ofHours(1)), T0.plus(Duration.ofHours(2)));
    }

    @Test
    void classificaPreamarEBaixaMarPeloVizinhoSeguinte() {
        LocalDate d = LocalDate.of(2023, 1, 1);
        List<TideExtreme> e = List.of(new TideExtreme(d, LocalTime.of(0, 0), 1.9), new TideExtreme(d, LocalTime.of(6, 11), 0.7),
                new TideExtreme(d, LocalTime.of(12, 8), 1.8), new TideExtreme(d, LocalTime.of(18, 34), 0.7));
        assertThat(TideExtremeClassifier.classify(e)).containsExactly("PREAMAR", "BAIXA_MAR", "PREAMAR", "BAIXA_MAR");
    }

    /** Conversão de fuso: a hora local da tábua (UTC-3) vira UTC (+3 h), inclusive virando o dia. */
    @Test
    void conversaoUtcMenos3ParaUtc() {
        assertThat(TimestampNormalizer.toInstant("2023-01-01T00:00", "UTC-03:00")).isEqualTo(Instant.parse("2023-01-01T03:00:00Z"));
        assertThat(TimestampNormalizer.toInstant("2026-10-10T21:53", "UTC-03:00")).isEqualTo(Instant.parse("2026-10-11T00:53:00Z"));
    }

    @Test
    void featuresDeMareComRespostaConhecida() {
        Instant t = Instant.parse("2023-01-01T09:00:00Z"); // 06:00 local
        Map<Instant, Double> hourly = new HashMap<>();
        // rampa: 1,00 em t-1 h, +0,10 por hora
        for (int k = -1; k <= 6; k++) hourly.put(t.plus(Duration.ofHours(k)), 1.0 + 0.1 * (k + 1));
        List<TideFeatureCalculator.Extreme> ex = List.of(
                new TideFeatureCalculator.Extreme(Instant.parse("2023-01-01T03:00:00Z"), 1.9, "PREAMAR"),   // 00:00 local
                new TideFeatureCalculator.Extreme(Instant.parse("2023-01-01T09:11:00Z"), 0.7, "BAIXA_MAR"),  // 06:11 local
                new TideFeatureCalculator.Extreme(Instant.parse("2023-01-01T15:08:00Z"), 1.8, "PREAMAR"),   // 12:08 local
                new TideFeatureCalculator.Extreme(Instant.parse("2023-01-01T21:34:00Z"), 0.7, "BAIXA_MAR"));
        var f = TideFeatureCalculator.at(t, hourly, ex);
        assertThat(f.height()).isEqualTo(1.1);
        assertThat(f.ahead().get(1)).isEqualTo(1.2);
        assertThat(f.ahead().get(6)).isEqualTo(1.7);
        assertThat(f.maxNext6h()).isEqualTo(1.7);
        assertThat(f.slopeMh()).isEqualTo(0.1);
        assertThat(f.rising()).isEqualTo(1);
        assertThat(f.hoursToNextHigh()).isEqualTo(6.13);         // 09:00Z -> 15:08Z = 6 h 08 min
        assertThat(f.rangeDayM()).isEqualTo(1.2);                // 1,9 - 0,7 no dia civil de Recife
    }

    @Test
    void featuresAusentesFicamNulasSemImputar() {
        Instant t = Instant.parse("2023-01-01T09:00:00Z");
        var f = TideFeatureCalculator.at(t, Map.of(t, 1.0), List.of());
        assertThat(f.height()).isEqualTo(1.0);
        assertThat(f.maxNext6h()).isNull();
        assertThat(f.ahead().values()).containsOnlyNulls();
        assertThat(f.rising()).isNull();
        assertThat(f.hoursToNextHigh()).isNull();
        assertThat(f.rangeDayM()).isNull();
    }
}
