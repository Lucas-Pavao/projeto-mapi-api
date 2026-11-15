package com.projeto.mapi.service.analise;

import com.projeto.mapi.service.analise.RainEventDetector.DetectedEvent;
import com.projeto.mapi.service.analise.RainEventDetector.HourSample;
import com.projeto.mapi.service.analise.RainEventDetector.Rules;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Detecção pura de eventos (ADR 0017): casos fixos e propriedades sobre séries aleatórias com semente fixa. */
class RainEventDetectorTest {

    private static final Instant T0 = Instant.parse("2024-03-12T00:00:00Z");
    private static final Rules RULES = new Rules(6, 1.0, 60.1);

    /** Série a partir de T0 (fim da 1ª hora = T0); null = lacuna. */
    private static List<HourSample> series(Double... mm) {
        List<HourSample> out = new ArrayList<>();
        for (int i = 0; i < mm.length; i++) {
            out.add(new HourSample(T0.plus(Duration.ofHours(i)), mm[i], mm[i] == null ? 0 : 4, 4, List.of()));
        }
        return out;
    }

    private static Double[] dry(int n) {
        Double[] d = new Double[n];
        java.util.Arrays.fill(d, 0.0);
        return d;
    }

    @Test
    void eventoSimples() {
        Double[] s = dry(30);
        s[10] = 2.0; s[11] = 6.0; s[12] = 1.0;
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(1);
        DetectedEvent e = ev.get(0);
        assertThat(e.startUtc()).isEqualTo(T0.plus(Duration.ofHours(9)));   // início da 1ª hora molhada
        assertThat(e.endUtc()).isEqualTo(T0.plus(Duration.ofHours(12)));    // fim da última
        assertThat(e.durationMin()).isEqualTo(180);
        assertThat(e.totalMm()).isCloseTo(9.0, within(1e-9));
        assertThat(e.maxHourlyMmH()).isEqualTo(6.0);
        assertThat(e.peakOffsetMin()).isEqualTo(60);
        assertThat(e.meanIntensityMmH()).isCloseTo(3.0, within(1e-9));
        assertThat(e.hasGap()).isFalse();
        assertThat(e.touchesSeriesEdge()).isFalse();
        assertThat(e.dryHoursBefore()).isNull();
        assertThat(e.expectedReadings()).isEqualTo(12);
        assertThat(e.receivedReadings()).isEqualTo(12);
    }

    @Test
    void separadosExatamentePeloIntervaloSecoSaoDois() {
        Double[] s = dry(40);
        s[10] = 3.0; s[17] = 3.0; // 6 horas secas (11..16) entre eles
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(2);
        assertThat(ev.get(1).dryHoursBefore()).isEqualTo(6.0);
    }

    @Test
    void separadosPorUmaHoraAMenosViramUm() {
        Double[] s = dry(40);
        s[10] = 3.0; s[16] = 3.0; // 5 horas secas
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).durationMin()).isEqualTo(7 * 60);
        assertThat(ev.get(0).totalMm()).isEqualTo(6.0);
    }

    @Test
    void lacunaNoMeioMarcaENaoQuebra() {
        Double[] s = dry(30);
        s[10] = 3.0; s[11] = null; s[12] = 3.0;
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(1);
        assertThat(ev.get(0).hasGap()).isTrue();
        assertThat(ev.get(0).expectedReadings()).isEqualTo(12);
        assertThat(ev.get(0).receivedReadings()).isEqualTo(8);
        assertThat(ev.get(0).totalMm()).isEqualTo(6.0); // lacuna não vira zero nem valor: só não soma
    }

    @Test
    void lacunaLongaSeparaMasMarcaOsDoisEApagaOIntervalo() {
        Double[] s = dry(40);
        s[10] = 3.0;
        for (int i = 11; i <= 18; i++) s[i] = null;
        s[19] = 3.0;
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(2);
        assertThat(ev).allMatch(DetectedEvent::hasGap);
        assertThat(ev.get(1).dryHoursBefore()).isNull(); // não se sabe se choveu na lacuna
    }

    @Test
    void ruidoAbaixoDoMinimoEhDescartado() {
        Double[] s = dry(30);
        s[10] = 0.4; s[11] = 0.4;
        assertThat(RainEventDetector.detect(series(s), RULES)).isEmpty();
    }

    @Test
    void serieTodaSecaNaoTemEvento() {
        assertThat(RainEventDetector.detect(series(dry(100)), RULES)).isEmpty();
    }

    @Test
    void eventoNaBordaDaSerieEncosta() {
        Double[] s = dry(20);
        s[0] = 5.0; s[19] = 5.0;
        List<DetectedEvent> ev = RainEventDetector.detect(series(s), RULES);
        assertThat(ev).hasSize(2).allMatch(DetectedEvent::touchesSeriesEdge);
    }

    @Test
    void sobrecargaSomaOExcessoHoraAHora() {
        Double[] s = dry(30);
        s[10] = 70.0; s[11] = 65.1; s[12] = 10.0;
        DetectedEvent e = RainEventDetector.detect(series(s), RULES).get(0);
        assertThat(e.hoursAboveCapacity()).isEqualTo(2);
        assertThat(e.excessOverCapacityMm()).isCloseTo(9.9 + 5.0, within(1e-9));
    }

    @Test
    void serieNaoContiguaEhRecusada() {
        List<HourSample> s = new ArrayList<>(series(dry(5)));
        s.remove(2);
        assertThatThrownBy(() -> RainEventDetector.detect(s, RULES)).isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * Propriedades em 500 séries aleatórias (semente fixa): eventos ordenados e disjuntos; entre dois eventos há pelo
     * menos dryGapHours horas; toda hora molhada fora de evento pertence a um bloco de ruído; total = soma das horas;
     * rodar duas vezes dá o mesmo resultado.
     */
    @Test
    void propriedades() {
        Random rnd = new Random(20261108L);
        for (int run = 0; run < 500; run++) {
            int n = 24 + rnd.nextInt(300);
            Double[] s = new Double[n];
            for (int i = 0; i < n; i++) {
                double u = rnd.nextDouble();
                s[i] = u < 0.05 ? null : u < 0.75 ? 0.0 : Math.round(rnd.nextDouble() * 300) / 10.0;
            }
            int gap = 1 + rnd.nextInt(10);
            Rules rules = new Rules(gap, rnd.nextDouble() * 3, 20);
            List<HourSample> series = series(s);
            List<DetectedEvent> ev = RainEventDetector.detect(series, rules);
            assertThat(RainEventDetector.detect(series, rules)).isEqualTo(ev);

            for (int k = 0; k < ev.size(); k++) {
                DetectedEvent e = ev.get(k);
                assertThat(s[e.firstIndex()]).isGreaterThan(0.0);
                assertThat(s[e.lastIndex()]).isGreaterThan(0.0);
                assertThat(e.totalMm()).isGreaterThanOrEqualTo(rules.minEventMm());
                double sum = 0;
                for (int i = e.firstIndex(); i <= e.lastIndex(); i++) if (s[i] != null) sum += s[i];
                assertThat(e.totalMm()).isCloseTo(sum, within(1e-9));
                // dentro do evento nunca há dryGapHours horas não molhadas seguidas
                int run2 = 0;
                for (int i = e.firstIndex(); i <= e.lastIndex(); i++) {
                    run2 = s[i] != null && s[i] > 0 ? 0 : run2 + 1;
                    assertThat(run2).isLessThan(gap);
                }
                if (k > 0) {
                    DetectedEvent prev = ev.get(k - 1);
                    assertThat(e.firstIndex()).isGreaterThan(prev.lastIndex());
                    if (e.dryHoursBefore() != null) {
                        assertThat(e.dryHoursBefore()).isEqualTo((double) (e.firstIndex() - prev.lastIndex() - 1));
                    }
                }
            }
            // toda hora molhada está num evento ou num bloco descartado (total < mínimo)
            for (int i = 0; i < n; i++) {
                if (s[i] == null || s[i] <= 0) continue;
                final int idx = i;
                boolean inEvent = ev.stream().anyMatch(e -> idx >= e.firstIndex() && idx <= e.lastIndex());
                if (!inEvent) assertThat(s[i]).isLessThan(rules.minEventMm());
            }
        }
    }
}
