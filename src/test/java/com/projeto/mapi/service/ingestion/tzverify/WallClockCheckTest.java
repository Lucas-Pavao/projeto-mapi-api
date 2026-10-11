package com.projeto.mapi.service.ingestion.tzverify;

import com.projeto.mapi.service.ingestion.tzverify.TzCorrelation.Verdict;
import com.projeto.mapi.service.ingestion.tzverify.WallClockCheck.Criteria;
import com.projeto.mapi.service.ingestion.tzverify.WallClockCheck.Sample;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

/** F2-DADOS-07, teste B: o método recupera o fuso conhecido de leituras sintéticas com latência. */
class WallClockCheckTest {

    /** 30 estações x 12 ciclos; latência de publicação 0..25 min; algumas estações atrasadas (horas). */
    private static List<Sample> synthetic(int truthOffset, int stations, boolean someStale) {
        Random rnd = new Random(5);
        List<Sample> out = new ArrayList<>();
        Instant base = Instant.parse("2026-10-10T12:00:00Z");
        for (int s = 0; s < stations; s++) {
            for (int c = 0; c < 12; c++) {
                Instant collected = base.plusSeconds(c * 300L);
                long latency = (long) (rnd.nextDouble() * 25 * 60);
                if (someStale && s % 7 == 0) latency += 5 * 3600L;
                long utcOfReading = collected.getEpochSecond() - latency;
                out.add(new Sample("st" + s, collected, utcOfReading + truthOffset * 3600L));
            }
        }
        return out;
    }

    @ParameterizedTest
    @ValueSource(ints = {-3, 0})
    void recuperaOFusoConhecidoMesmoComEstacoesAtrasadas(int truth) {
        var r = WallClockCheck.evaluate(synthetic(truth, 30, true), Criteria.preRegistered());

        assertThat(r.acceptedOffsets()).containsExactly(truth);
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPT);
        assertThat(r.offsetHours()).isEqualTo(truth);
    }

    @Test
    void offsetImplausivelEhIdentificadoMasNaoAceito() {
        var r = WallClockCheck.evaluate(synthetic(2, 30, false), Criteria.preRegistered());

        assertThat(r.acceptedOffsets()).containsExactly(2);
        assertThat(r.verdict()).isEqualTo(Verdict.IMPLAUSIBLE_OFFSET);
    }

    @Test
    void poucasEstacoesDaPoderInsuficiente() {
        var r = WallClockCheck.evaluate(synthetic(-3, 3, false), Criteria.preRegistered());

        assertThat(r.verdict()).isEqualTo(Verdict.INSUFFICIENT_DATA);
        assertThat(r.offsetHours()).isNull();
    }

    @Test
    void latenciaFixaDe10MinutosEmUtcMenos3() {
        // rótulo = UTC-3 com 10 min de latência em todas as leituras
        List<Sample> samples = new ArrayList<>();
        Instant base = Instant.parse("2026-10-10T12:00:00Z");
        for (int s = 0; s < 30; s++) {
            for (int c = 0; c < 12; c++) {
                Instant collected = base.plusSeconds(c * 300L);
                samples.add(new Sample("st" + s, collected, collected.getEpochSecond() - 3 * 3600L - 600));
            }
        }
        var r = WallClockCheck.evaluate(samples, Criteria.preRegistered());
        assertThat(r.offsetHours()).isEqualTo(-3);
    }
}
