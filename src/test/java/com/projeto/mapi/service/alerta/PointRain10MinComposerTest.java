package com.projeto.mapi.service.alerta;

import com.projeto.mapi.repository.analise.PointRain10MinRepository;
import com.projeto.mapi.repository.analise.PointRain10MinRepository.Block;
import com.projeto.mapi.service.alerta.PointRain10MinComposer.Result;
import com.projeto.mapi.service.alerta.PointRain10MinComposer.Slot;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PointRain10MinComposerTest {

    static final Instant T0 = Instant.parse("2026-11-17T12:00:00Z");

    static Instant t(int min) {
        return T0.plusSeconds(min * 60L);
    }

    /** Bloco ao vivo (início = ts): tamanho vem da leitura anterior. */
    static Block live(String id, double km, double lat, double lon, int endMin, int prevMin, double mm) {
        return new Block(id, lat, lon, km, "CEMADEN", id, t(endMin), t(endMin), t(prevMin), mm, null, null);
    }

    static Block ped(String id, double km, int endMin, int startMin, double mm) {
        return new Block(id, -8.05, -34.95, km, "CEMADEN_PED", id, t(endMin), t(startMin), null, mm, null, null);
    }

    @Test
    void blocoDe60MinEEspalhadoPorIgualEMarcado() {
        Result r = PointRain10MinComposer.compose(List.of(ped("A", 1.0, 60, 0, 6.0)), Map.of(), T0, t(60),
                PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.slots()).hasSize(6);
        assertThat(r.slots()).allSatisfy(s -> {
            assertThat(s.mm()).isEqualTo(1.0);
            assertThat(s.longBlock()).isTrue();
            assertThat(s.blockMinutes()).isEqualTo(60);
        });
    }

    @Test
    void maiorValorEntreAsValidasAte5KmEForaDoRaioNaoConta() {
        List<Block> b = List.of(ped("A", 1.0, 10, 0, 2.0), ped("B", 4.0, 10, 0, 3.0), ped("C", 8.0, 10, 0, 9.0));
        Slot s = PointRain10MinComposer.compose(b, Map.of(), T0, t(10), PointRain10MinComposer.LiveFilter.NONE).slots().get(0);
        assertThat(s.mm()).isEqualTo(3.0);
        assertThat(s.stationId()).isEqualTo("B");
        assertThat(s.nValid()).isEqualTo(2);
        assertThat(s.longBlock()).isFalse();
    }

    @Test
    void passoSemEstacaoFicaNuloNuncaZero() {
        Result r = PointRain10MinComposer.compose(List.of(ped("A", 1.0, 10, 0, 2.0)), Map.of(), T0, t(30),
                PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.slots()).extracting(Slot::mm).containsExactly(2.0, null, null);
    }

    @Test
    void estacaoReprovadaNoMesSai() {
        Map<String, String> month = Map.of(PointRain10MinRepository.key("A", Instant.parse("2026-11-01T00:00:00Z")), "FAIL");
        Result r = PointRain10MinComposer.compose(List.of(ped("A", 1.0, 10, 0, 2.0)), month, T0, t(10),
                PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.slots().get(0).mm()).isNull();
        assertThat(r.dropped()).extracting(PointRain10MinComposer.Dropped::reason).containsExactly("ESTACAO_REPROVADA_NO_MES");
    }

    @Test
    void picoIsoladoSaiPelaRegraDoRainSpatialCheck() {
        List<Block> b = new ArrayList<>();
        b.add(live("PICO", 1.0, -8.050, -34.950, 60, 50, 40.7));          // 40,7 mm numa hora
        b.add(live("V1", 3.0, -8.060, -34.950, 60, 0, 0.0));               // vizinhas a ~1 km, secas
        b.add(live("V2", 7.0, -8.050, -34.960, 60, 0, 0.2));
        Result r = PointRain10MinComposer.compose(b, Map.of(), t(50), t(60), PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.dropped()).extracting(PointRain10MinComposer.Dropped::reason).contains("PICO_ISOLADO");
        assertThat(r.slots().get(0).mm()).isEqualTo(0.0);                 // sobra a V1 (seca)
    }

    @Test
    void chuvaComVizinhaMolhadaNaoEPico() {
        List<Block> b = List.of(live("A", 1.0, -8.050, -34.950, 60, 50, 15.0), live("V1", 3.0, -8.060, -34.950, 60, 50, 5.0),
                live("V2", 3.0, -8.040, -34.950, 60, 50, 0.0));
        Result r = PointRain10MinComposer.compose(b, Map.of(), t(50), t(60), PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.dropped()).isEmpty();
        assertThat(r.slots().get(0).mm()).isEqualTo(15.0);
    }

    @Test
    void blocoAoVivoSemLeituraAnteriorNaoTemTamanhoEFicaFora() {
        Block b = new Block("A", -8.05, -34.95, 1.0, "CEMADEN", "A", t(10), t(10), null, 3.0, null, null);
        Result r = PointRain10MinComposer.compose(List.of(b), Map.of(), T0, t(10), PointRain10MinComposer.LiveFilter.NONE);
        assertThat(r.slots().get(0).mm()).isNull();
        assertThat(r.newestReading()).isNull();
    }

    @Test
    void filtroAoVivoRetiraComMotivo() {
        Result r = PointRain10MinComposer.compose(List.of(ped("A", 1.0, 10, 0, 2.0)), Map.of(), T0, t(10),
                blk -> "QC_TRAVADA_EM_ZERO");
        assertThat(r.slots().get(0).mm()).isNull();
        assertThat(r.dropped()).extracting(PointRain10MinComposer.Dropped::reason).containsExactly("QC_TRAVADA_EM_ZERO");
    }
}
