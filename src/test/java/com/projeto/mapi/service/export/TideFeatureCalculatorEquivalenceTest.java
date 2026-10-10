package com.projeto.mapi.service.export;

import static org.assertj.core.api.Assertions.assertThat;

import com.projeto.mapi.service.export.TideFeatureCalculator.Extreme;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * F2-BACK-22: o índice (busca binária + agregado por dia) não muda nenhum valor em relação à varredura linear
 * original; a única diferença permitida é {@code tide_rising} com derivada nula (agora desempatada pelo passo à frente).
 */
class TideFeatureCalculatorEquivalenceTest {

    private static final ZoneId RECIFE = ZoneId.of("America/Recife");

    // ---- implementação de referência: cópia literal da versão anterior (varredura linear por hora) ----
    private static Double refHoursToNextHigh(Instant t, List<Extreme> extremes) {
        for (Extreme e : extremes) {
            if (e.ts().isAfter(t) && "PREAMAR".equals(e.kind())) {
                Duration d = Duration.between(t, e.ts());
                return d.compareTo(Duration.ofHours(24)) > 0 ? null : r2(d.toSeconds() / 3600.0);
            }
        }
        return null;
    }

    private static Double refRange(Instant t, List<Extreme> extremes) {
        LocalDate day = t.atZone(RECIFE).toLocalDate();
        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY;
        int n = 0;
        for (Extreme e : extremes) {
            if (e.ts().atZone(RECIFE).toLocalDate().equals(day)) {
                min = Math.min(min, e.level());
                max = Math.max(max, e.level());
                n++;
            }
        }
        return n < 2 ? null : r2(max - min);
    }

    private static double r2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static Integer refRising(Double slope) {
        return slope == null || slope == 0.0 ? null : (slope > 0 ? 1 : 0);
    }

    // ---- dados sintéticos: maré semidiurna quantizada em 0,1 m (como a tábua), com lacunas ----
    private final Instant start = Instant.parse("2023-01-01T00:00:00Z");
    private final int hours = 24 * 120;
    private final Map<Instant, Double> hourly = new HashMap<>();
    private final List<Extreme> extremes = new ArrayList<>();

    private void build() {
        Random rnd = new Random(42);
        for (int i = -2; i < hours + 8; i++) {
            if (rnd.nextInt(200) == 0) continue; // lacuna
            double v = 1.0 + 0.9 * Math.sin(2 * Math.PI * i / 12.42);
            hourly.put(start.plus(Duration.ofHours(i)), Math.round(v * 10.0) / 10.0);
        }
        for (double m = -300; m < hours * 60 + 3000; m += 12.42 * 30) { // extremos a cada ~6,21 h
            boolean high = ((int) Math.round(m / (12.42 * 30))) % 2 == 0;
            extremes.add(new Extreme(start.plusSeconds((long) (m * 60)), high ? 1.8 : 0.2 + (rnd.nextInt(3) / 10.0),
                    high ? "PREAMAR" : "BAIXA_MAR"));
        }
    }

    @Test
    void indiceNaoMudaValoresEMesmoHashDoResultado() throws Exception {
        build();
        var idx = TideFeatureCalculator.index(extremes);
        StringBuilder antigo = new StringBuilder();
        StringBuilder novo = new StringBuilder();
        int rotaDesempatada = 0;
        for (int i = 0; i < hours; i++) {
            Instant t = start.plus(Duration.ofHours(i));
            var f = TideFeatureCalculator.at(t, hourly, idx);
            assertThat(f.hoursToNextHigh()).as("hoursToNextHigh %s", t).isEqualTo(refHoursToNextHigh(t, extremes));
            assertThat(f.rangeDayM()).as("range %s", t).isEqualTo(refRange(t, extremes));
            Integer refRising = refRising(f.slopeMh());
            boolean desempatada = refRising == null && f.slopeMh() != null;
            if (desempatada) rotaDesempatada++;
            else assertThat(f.rising()).as("rising %s", t).isEqualTo(refRising);
            // serialização "CSV" igual nas duas versões, exceto rising desempatado (a diferença permitida)
            String comum = f.height() + "|" + f.ahead() + "|" + f.maxNext6h() + "|" + f.slopeMh() + "|";
            antigo.append(comum).append(refHoursToNextHigh(t, extremes)).append('|').append(refRange(t, extremes))
                    .append('|').append(refRising).append('\n');
            novo.append(comum).append(f.hoursToNextHigh()).append('|').append(f.rangeDayM()).append('|')
                    .append(desempatada ? null : f.rising()).append('\n');
        }
        assertThat(rotaDesempatada).as("o conjunto exercita derivada nula").isGreaterThan(10);
        assertThat(sha256(novo.toString())).isEqualTo(sha256(antigo.toString()));
    }

    @Test
    void derivadaNulaEmPreamarEBaixaMarDesempataPeloPassoAFrente() {
        Instant t = Instant.parse("2023-01-01T12:00:00Z");
        Map<Instant, Double> h = new HashMap<>();
        // pico: 1,8 | 2,1 | 1,8 -> derivada central 0; passo à frente < 0 -> não está subindo
        h.put(t.minus(Duration.ofHours(1)), 1.8);
        h.put(t, 2.1);
        h.put(t.plus(Duration.ofHours(1)), 1.8);
        var f = TideFeatureCalculator.at(t, h, List.of());
        assertThat(f.slopeMh()).isEqualTo(0.0);
        assertThat(f.rising()).isEqualTo(0);
        // vale: 0,5 | 0,3 | 0,5 -> passo à frente > 0
        h.put(t.minus(Duration.ofHours(1)), 0.5);
        h.put(t, 0.3);
        h.put(t.plus(Duration.ofHours(1)), 0.5);
        assertThat(TideFeatureCalculator.at(t, h, List.of()).rising()).isEqualTo(1);
        // plateau: tudo igual -> sem direção, fica nulo (o CSV marca tide_rising_missing = 1)
        h.put(t.minus(Duration.ofHours(1)), 1.0);
        h.put(t, 1.0);
        h.put(t.plus(Duration.ofHours(1)), 1.0);
        assertThat(TideFeatureCalculator.at(t, h, List.of()).rising()).isNull();
        // sem a hora seguinte: insumo ausente, nulo
        h.remove(t.plus(Duration.ofHours(1)));
        assertThat(TideFeatureCalculator.at(t, h, List.of()).rising()).isNull();
    }

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }
}
