package com.projeto.mapi.service.station;

import com.projeto.mapi.service.station.StationMatcher.Canonical;
import com.projeto.mapi.service.station.StationMatcher.Station;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StationMatcherTest {

    private static Station ped(String id, String name, double lat, double lon) {
        return new Station("CEMADEN_PED", id, name, lat, lon, "RECIFE");
    }

    private static Station live(String id, String name, double lat, double lon) {
        return new Station("CEMADEN", id, name, lat, lon, "RECIFE");
    }

    @Test
    void mesmoCodigoEMesmaCoordenada_viraUmaCanonicaComDoisApelidos() {
        List<Canonical> out = StationMatcher.unify(
                List.of(ped("261160610A", "Alto Mandu", -8.019, -34.931)),
                List.of(live("261160610A", "[CEMADEN] Alto Mandu", -8.019, -34.931)), 150);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).canonicalId()).isEqualTo("261160610A");
        assertThat(out.get(0).aliases()).hasSize(2);
        assertThat(out.get(0).aliases().get(1).nameSimilarity()).isEqualTo(1.0);
    }

    @Test
    void codigoDiferenteAte150m_unificaPelaCoordenada_eNomeSoVaiParaORegistro() {
        // 0,001° de latitude ≈ 111 m
        List<Canonical> out = StationMatcher.unify(
                List.of(ped("261160610A", "Alto Mandu", -8.019, -34.931)),
                List.of(live("APAC-99", "[APAC] Escola Municipal", -8.020, -34.931)), 150);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).aliases().get(1).distanceM()).isBetween(100.0, 120.0);
        assertThat(out.get(0).aliases().get(1).nameSimilarity()).isZero();
    }

    @Test
    void acimaDe150m_ficaSeparada_mesmoComNomeIgual() {
        List<Canonical> out = StationMatcher.unify(
                List.of(ped("261160610A", "Alto Mandu", -8.019, -34.931)),
                List.of(live("261160699A", "[CEMADEN] Alto Mandu", -8.021, -34.931)), 150); // ~222 m
        assertThat(out).extracting(Canonical::canonicalId).containsExactly("261160610A", "261160699A");
    }

    @Test
    void duasEstacoesDaPedNuncaSeFundem() {
        List<Canonical> out = StationMatcher.unify(
                List.of(ped("260345401A", "Timbi", -8.0200, -34.977), ped("260345401G", "Convento", -8.0201, -34.977)),
                List.of(), 150);
        assertThat(out).hasSize(2);
    }

    @Test
    void mesmoCodigoLonge_naoColideComACanonicaDaPed() {
        List<Canonical> out = StationMatcher.unify(
                List.of(ped("261160610A", "Alto Mandu", -8.019, -34.931)),
                List.of(live("261160610A", "Alto Mandu", -8.10, -34.931)), 150);
        assertThat(out).extracting(Canonical::canonicalId).containsExactly("261160610A", "CEMADEN:261160610A");
    }

    @Test
    void codigoIbgeComDigitoVerificador() {
        assertThat(StationMatcher.ibgeCode("261160610A")).isEqualTo("2611606"); // Recife
        assertThat(StationMatcher.ibgeCode("260345401A")).isEqualTo("2603454"); // Camaragibe
        assertThat(StationMatcher.ibgeCode("260790201A")).isEqualTo("2607901"); // Jaboatão dos Guararapes
        assertThat(StationMatcher.ibgeCode("260960001A")).isEqualTo("2609600"); // Olinda
        assertThat(StationMatcher.ibgeCode("A301")).isNull();
    }
}
