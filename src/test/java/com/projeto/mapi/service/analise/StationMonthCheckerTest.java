package com.projeto.mapi.service.analise;

import com.projeto.mapi.service.analise.StationMonthChecker.Result;
import com.projeto.mapi.service.analise.StationMonthChecker.Rules;
import com.projeto.mapi.service.analise.StationMonthChecker.StationMonth;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Checagem espacial mensal (ADR 0022): os três defeitos conhecidos e os casos de fronteira. */
class StationMonthCheckerTest {

    private static final int H = 720;
    private static final Rules R = Rules.defaults();

    /** Série de 720 h com 2 mm a cada 10 h (144 mm no mês), blocos cobrindo a hora inteira. */
    private static double[] wet(double factor) {
        double[] mm = new double[H];
        for (int i = 0; i < H; i++) mm[i] = i % 10 == 0 ? 2.0 * factor : 0.0;
        return mm;
    }

    private static double[] cov(double minutes) {
        double[] c = new double[H];
        Arrays.fill(c, minutes);
        return c;
    }

    private static StationMonth st(String id, double latOffset, double[] mm, double[] cov) {
        return new StationMonth(id, -8.10 + latOffset, -34.92, mm, cov);
    }

    private static Map<String, Result> run(StationMonth... s) {
        return StationMonthChecker.check(List.of(s), R).stream()
                .collect(Collectors.toMap(Result::stationId, Function.identity()));
    }

    @Test
    void travadoEmZeroReprova() {
        var r = run(st("A", 0, wet(1), cov(60)), st("B", 0.01, wet(1.1), cov(60)), st("C", 0.02, wet(0.9), cov(60)),
                st("TRAVADO", 0.005, new double[H], cov(60)));
        assertThat(r.get("TRAVADO").status()).isEqualTo("FAIL");
        assertThat(r.get("TRAVADO").reasons()).containsExactly("STUCK_ZERO");
        assertThat(r.get("A").status()).isEqualTo("PASS");
    }

    @Test
    void entupimentoReprovaEExcessoTambem() {
        var r = run(st("A", 0, wet(1), cov(60)), st("B", 0.01, wet(1), cov(60)), st("C", 0.02, wet(1), cov(60)),
                st("ENTUPIDO", 0.005, wet(0.3), cov(60)), st("EXAGERA", 0.015, wet(2.5), cov(60)));
        assertThat(r.get("ENTUPIDO").reasons()).containsExactly("UNDERCATCH");
        assertThat(r.get("EXAGERA").reasons()).containsExactly("OVERCATCH");
        assertThat(r.get("A").usable()).isTrue();
    }

    @Test
    void coberturaParcialDosBlocosReprovaMesmoComHoraPreenchida() {
        // toda hora tem leitura, mas os blocos cobrem só 30 min: metade dos blocos faltou (conta zero na V22)
        var r = run(st("A", 0, wet(1), cov(60)), st("B", 0.01, wet(1), cov(60)), st("C", 0.02, wet(1), cov(60)),
                st("PARCIAL", 0.005, wet(0.5), cov(30)));
        assertThat(r.get("PARCIAL").hourCoverage()).isEqualTo(1.0);
        assertThat(r.get("PARCIAL").blockCoverage()).isEqualTo(0.5);
        assertThat(r.get("PARCIAL").reasons()).contains("PARTIAL_COVERAGE");
    }

    @Test
    void comparaSoNasHorasEmComumEDiasParadosNaoReprovam() {
        // a estação só operou a 1a metade; nas horas dela, chove igual às vizinhas e os blocos cobrem as horas:
        // não é entupimento nem cobertura parcial (os dias parados já são lacuna)
        double[] half = wet(1);
        double[] c = cov(60);
        for (int i = H / 2; i < H; i++) {
            half[i] = Double.NaN;
            c[i] = Double.NaN;
        }
        var r = run(st("A", 0, wet(1), cov(60)), st("B", 0.01, wet(1), cov(60)), st("METADE", 0.005, half, c));
        assertThat(r.get("METADE").ratioMedian()).isEqualTo(1.0);
        assertThat(r.get("METADE").blockCoverage()).isEqualTo(0.5);
        assertThat(r.get("METADE").blockCoverageInHours()).isEqualTo(1.0);
        assertThat(r.get("METADE").status()).isEqualTo("PASS");
    }

    @Test
    void mesSecoOuSemVizinhasFicaNaoChecadoEUsavel() {
        var r = run(st("A", 0, new double[H], cov(60)), st("B", 0.01, new double[H], cov(60)),
                st("LONGE", 1.0, wet(1), cov(60)));
        assertThat(r.get("A").status()).isEqualTo("UNCHECKED");
        assertThat(r.get("A").usable()).isTrue();
        assertThat(r.get("LONGE").reasons()).containsExactly("FEW_WET_NEIGHBORS");
    }

    @Test
    void semLeituraNoMesFicaOffline() {
        double[] none = new double[H];
        Arrays.fill(none, Double.NaN);
        var r = run(st("A", 0, wet(1), cov(60)), st("OFF", 0.01, none, cov(Double.NaN)));
        assertThat(r.get("OFF").status()).isEqualTo("OFFLINE");
        assertThat(r.get("OFF").usable()).isFalse();
    }
}
