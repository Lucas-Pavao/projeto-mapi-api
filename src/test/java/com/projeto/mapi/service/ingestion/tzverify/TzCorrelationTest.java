package com.projeto.mapi.service.ingestion.tzverify;

import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Criteria;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Decision;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Evaluation;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Reading;
import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Verdict;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** F2-DADOS-07: o método recupera o deslocamento conhecido de séries sintéticas. */
class TzCorrelationTest {

    private static final int DAYS = 120;

    /** Chuva horária de referência em UTC (chave = hora do fim do intervalo), com tempestades esparsas. */
    private static Map<Long, Double> referenceRain(long seed) {
        Random rnd = new Random(seed);
        Map<Long, Double> ref = new TreeMap<>();
        long base = 20000L * 24; // dia 20000 desde 1970
        for (int d = 0; d < DAYS; d++) {
            for (int h = 1; h <= 24; h++) ref.put(base + d * 24L + h, 0.0);
            if (rnd.nextDouble() < 0.30) {
                int start = 1 + rnd.nextInt(18);
                int len = 2 + rnd.nextInt(6);
                for (int i = 0; i < len && start + i <= 24; i++) {
                    ref.put(base + d * 24L + start + i, -Math.log(1 - rnd.nextDouble()) * 3.0 + 0.2);
                }
            }
        }
        return ref;
    }

    /** Leituras de 15 min com rótulo = UTC + offsetHours, ruído multiplicativo e algumas horas sem leitura. */
    private static List<Reading> stationReadings(Map<Long, Double> ref, int offsetHours, long seed, double sigma) {
        Random rnd = new Random(seed);
        List<Reading> out = new ArrayList<>();
        for (Map.Entry<Long, Double> e : ref.entrySet()) {
            if (rnd.nextDouble() < 0.03) continue; // lacuna
            double hourly = e.getValue() * Math.exp(sigma * rnd.nextGaussian());
            for (int q = 1; q <= 4; q++) {
                long utc = e.getKey() * 3600L - (4 - q) * 900L;
                out.add(new Reading(utc + offsetHours * 3600L, hourly / 4.0));
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, 0})
    void recuperaOffsetPlausivelEAceita(int truth) {
        Map<Long, Double> ref = referenceRain(7);
        Evaluation ev = TzCorrelation.evaluate(stationReadings(ref, truth, 11, 0.5), ref, 3, 300, 42);

        assertThat(ev.bestOffset()).isEqualTo(truth);
        assertThat(ev.margin()).isGreaterThan(0.05);
        Decision d = TzCorrelation.decide(ev, Criteria.preRegistered());
        assertThat(d.verdict()).isEqualTo(Verdict.ACCEPT);
        assertThat(d.offsetHours()).isEqualTo(truth);
    }

    @Test
    void dadoEmUtcMenos3ProduzArgmaxMenos3EIntervaloContemOEstimado() {
        Map<Long, Double> ref = referenceRain(3);
        Evaluation ev = TzCorrelation.evaluate(stationReadings(ref, -3, 5, 0.6), ref, 3, 400, 1);

        var best = ev.of(-3);
        assertThat(best.spearman()).isGreaterThan(0.8);
        assertThat(best.spearmanLo()).isLessThanOrEqualTo(best.spearman());
        assertThat(best.spearmanHi()).isGreaterThanOrEqualTo(best.spearman());
        assertThat(ev.of(0).spearman()).isLessThan(best.spearman() - 0.3);
        assertThat(best.nWetHours()).isGreaterThan(100);
    }

    @Test
    void offsetImplausivelNaoEAceito() {
        Map<Long, Double> ref = referenceRain(9);
        Evaluation ev = TzCorrelation.evaluate(stationReadings(ref, 2, 13, 0.4), ref, 3, 200, 3);

        assertThat(ev.bestOffset()).isEqualTo(2); // o método enxerga o deslocamento...
        Decision d = TzCorrelation.decide(ev, Criteria.preRegistered());
        assertThat(d.verdict()).isEqualTo(Verdict.IMPLAUSIBLE_OFFSET); // ...mas a fonte não sai da quarentena
    }

    @Test
    void ruidoIndependenteNaoEAceito() {
        Map<Long, Double> ref = referenceRain(21);
        Map<Long, Double> other = referenceRain(22); // outra "meteorologia": sem relação com a referência
        Evaluation ev = TzCorrelation.evaluate(stationReadings(other, -3, 23, 0.5), ref, 3, 200, 5);

        Decision d = TzCorrelation.decide(ev, Criteria.preRegistered());
        assertThat(d.verdict()).isNotEqualTo(Verdict.ACCEPT);
    }

    @Test
    void poucaChuvaDaPoderInsuficiente() {
        Map<Long, Double> ref = new TreeMap<>();
        long base = 20000L * 24;
        for (int h = 1; h <= 24 * 10; h++) ref.put(base + h, 0.0);
        ref.put(base + 30, 5.0);
        ref.put(base + 31, 2.0);
        Evaluation ev = TzCorrelation.evaluate(stationReadings(ref, -3, 1, 0.1), ref, 3, 100, 1);

        assertThat(TzCorrelation.decide(ev, Criteria.preRegistered()).verdict()).isEqualTo(Verdict.INSUFFICIENT_DATA);
    }

    @Test
    void horaComLeiturasInsuficientesFicaAusenteENaoZero() {
        List<Reading> r = List.of(new Reading(3600L * 10, 1.0), new Reading(3600L * 10 - 900, 1.0));
        assertThat(TzCorrelation.aggregateHourly(r, 0, 3)).isEmpty();
        assertThat(TzCorrelation.aggregateHourly(r, 0, 2)).containsEntry(10L, 2.0);
    }

    @Test
    void instanteExatoPertenceAoIntervaloQueTermina() {
        // 03:00 no rótulo com h=-3 => 06:00 UTC => hora rotulada 6 (intervalo (5,6])
        Map<Long, Double> agg = TzCorrelation.aggregateHourly(List.of(new Reading(3L * 3600, 1.0)), -3, 1);
        assertThat(agg).containsOnlyKeys(6L);
        // 03:15 => hora 7
        assertThat(TzCorrelation.aggregateHourly(List.of(new Reading(3L * 3600 + 900, 1.0)), -3, 1)).containsOnlyKeys(7L);
    }

    @Test
    void postosComEmpatesUsamAMedia() {
        assertThat(TzCorrelation.ranks(new double[]{0, 0, 5, 1})).containsExactly(1.5, 1.5, 4.0, 3.0);
    }

    @Test
    void decisaoDaFonteExigeEstacoesConcordantes() {
        Decision a = new Decision(Verdict.ACCEPT, -3, "");
        Decision weak = new Decision(Verdict.WEAK_CORRELATION, null, "");
        Decision other = new Decision(Verdict.ACCEPT, 0, "");
        Decision contrary = new Decision(Verdict.IMPLAUSIBLE_OFFSET, 2, "");

        assertThat(TzCorrelation.decideSource(List.of(a, a, weak), 2).verdict()).isEqualTo(Verdict.ACCEPT);
        assertThat(TzCorrelation.decideSource(List.of(a, weak), 2).verdict()).isEqualTo(Verdict.INSUFFICIENT_DATA);
        assertThat(TzCorrelation.decideSource(List.of(a, other), 2).verdict()).isEqualTo(Verdict.AMBIGUOUS);
        assertThat(TzCorrelation.decideSource(List.of(a, a, contrary), 2).verdict()).isEqualTo(Verdict.AMBIGUOUS);
    }
}
