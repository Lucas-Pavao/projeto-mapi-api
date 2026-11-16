package com.projeto.mapi.integration;

import com.projeto.mapi.service.flood.PointRainInputs;
import com.projeto.mapi.service.flood.impl.PointRainInputServiceImpl;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Chuva da previsão ao vivo pela série medida (ADR 0007, emenda 3): lacuna nunca vira zero. */
class PointRainInputIntegrationTest extends AbstractApiIntegrationTest {

    private static final Instant AT = Instant.parse("2026-11-08T14:00:00Z");
    /** 40 min depois do fim da hora: fora da tolerância de latência (30 min). */
    private static final Instant NOW = AT.plus(Duration.ofMinutes(40));
    private static final double LAT = -8.0, LON = -34.9;

    @Autowired JdbcTemplate jdbc;
    @Autowired PointRainInputServiceImpl service;
    private UUID ped, live;

    @BeforeEach
    void clean() throws Exception {
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED'");
        jdbc.update("DELETE FROM point_station_link WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "A", -8.0, -34.9);
        ObservationFixtures.station(jdbc, "CEMADEN", "LIVE-A", -8.0, -34.9);      // mesma estação física
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "B", -8.005, -34.9);
        ObservationFixtures.station(jdbc, "CEMADEN", "SO-VIVO", -8.01, -34.9);    // sem PED: não entra
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "LONGE", -8.2, -34.9);   // > 5 km
        mockMvc.perform(as(post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
        ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        live = ObservationFixtures.run(jdbc, "CEMADEN");
        service.invalidate();
    }

    private void check(String id, String status) {
        jdbc.update("INSERT INTO station_month_check (source, station_id, month_start_utc, status, reasons, hours_in_month, "
                + "hours_with_value, coverage, n_neighbors, rule_version) VALUES ('CEMADEN_PED', ?, '2026-11-01T00:00:00Z', ?, "
                + "'{}'::text[], 720, 700, 0.97, 3, 'chuva-ponto-v4')", id, status);
    }

    private void pedHours(String id, int hours, double mm) {
        for (int i = 0; i < hours; i++) ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", id, AT.minus(Duration.ofHours(i)), mm);
    }

    private PointRainInputs inputs() {
        service.invalidate();
        return service.inputsAt(LAT, LON, NOW);
    }

    @Test
    void serieCompleta_maiorEntreValidas_eAcumuladosSomamAsHoras() {
        pedHours("A", 24, 1.0);
        pedHours("B", 24, 0.5);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "LONGE", AT, 9.0);   // fora do raio
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "SO-VIVO", AT, 8.0);    // só ao vivo: fora
        PointRainInputs r = inputs();
        assertThat(r.source()).isEqualTo("MEASURED");
        assertThat(r.hourEndUtc()).isEqualTo(AT);
        assertThat(r.current()).isEqualTo(1.0);
        assertThat(r.acc3h()).isEqualTo(3.0);
        assertThat(r.acc24h()).isEqualTo(24.0);
        assertThat(r.absent()).isEmpty();
        assertThat(r.insufficient()).isFalse();
        assertThat(r.values().get(0).stations()).extracting(PointRainInputs.Station::stationId).containsExactly("A");
        assertThat(r.nValid()).isEqualTo(2);
    }

    @Test
    void horaSemLeitura_vaiAusente_nuncaZero() {
        pedHours("A", 24, 1.0);
        jdbc.update("DELETE FROM observation WHERE source = 'CEMADEN_PED' AND station_id = 'A' AND ts_utc = ?::timestamptz",
                AT.minus(Duration.ofHours(4)).toString());                              // buraco há 4 h
        PointRainInputs r = inputs();
        assertThat(r.current()).isEqualTo(1.0);
        assertThat(r.acc3h()).isEqualTo(3.0);                                       // janela sem o buraco
        assertThat(r.acc6h()).isNull();                                             // ausente, não 5,0 nem 0
        assertThat(r.acc24h()).isNull();
        assertThat(r.absent()).containsExactly("rainfall_6h_accumulated", "rainfall_12h_accumulated", "rainfall_24h_accumulated");
        assertThat(r.values().get(2).hoursWithValue()).isEqualTo(5);
        assertThat(r.insufficient()).isFalse();
    }

    @Test
    void todasAsEstacoesFora_viraInsuficiente() {
        pedHours("A", 24, 1.0);
        pedHours("B", 24, 2.0);
        check("A", "FAIL");
        check("B", "OFFLINE");
        PointRainInputs r = inputs();
        assertThat(r.current()).isNull();
        assertThat(r.acc3h()).isNull();
        assertThat(r.insufficient()).isTrue();                                      // regra de degradação: UNKNOWN
        assertThat(r.nValid()).isZero();
        assertThat(r.absent()).hasSize(5);
    }

    @Test
    void estacaoReprovadaNoMes_ficaFora() {
        pedHours("A", 24, 5.0);                                                     // maior valor, mas reprovada
        pedHours("B", 24, 0.5);
        check("A", "FAIL");
        check("B", "PASS");
        PointRainInputs r = inputs();
        assertThat(r.current()).isEqualTo(0.5);
        assertThat(r.values().get(0).stations()).extracting(PointRainInputs.Station::stationId).containsExactly("B");
        assertThat(r.nValid()).isEqualTo(1);
    }

    @Test
    void aoVivoPreencheAHoraQueAPedAindaNaoTem_ePedVenceNaMesmaHora() {
        for (int i = 1; i < 24; i++) ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "A", AT.minus(Duration.ofHours(i)), 1.0);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "LIVE-A", AT, 0.4);                         // PED ainda não chegou
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "LIVE-A", AT.minus(Duration.ofHours(1)), 7.0); // PED tem 1,0: vence
        PointRainInputs r = inputs();
        assertThat(r.current()).isEqualTo(0.4);
        assertThat(r.values().get(0).stations().get(0).source()).isEqualTo("CEMADEN");
        assertThat(r.acc3h()).isEqualTo(2.4);                                       // 0,4 + 1,0 (PED) + 1,0
        assertThat(r.acc24h()).isEqualTo(23.4);
    }

    @Test
    void ultimaHoraAindaNaoChegou_dentroDaTolerancia_recuaUmaHora() {
        for (int i = 1; i <= 24; i++) ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "A", AT.minus(Duration.ofHours(i)), 1.0);
        service.invalidate();
        PointRainInputs cedo = service.inputsAt(LAT, LON, AT.plus(Duration.ofMinutes(5)));
        assertThat(cedo.hourEndUtc()).isEqualTo(AT.minus(Duration.ofHours(1)));     // referência recua
        assertThat(cedo.current()).isEqualTo(1.0);
        assertThat(cedo.acc24h()).isEqualTo(24.0);
        PointRainInputs tarde = inputs();                                           // 40 min depois: lacuna de verdade
        assertThat(tarde.hourEndUtc()).isEqualTo(AT);
        assertThat(tarde.current()).isNull();
        assertThat(tarde.insufficient()).isTrue();
    }
}
