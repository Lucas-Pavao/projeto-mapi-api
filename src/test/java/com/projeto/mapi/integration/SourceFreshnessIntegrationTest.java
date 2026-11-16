package com.projeto.mapi.integration;

import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.service.ingestion.freshness.SourceFreshnessService;
import com.projeto.mapi.support.AbstractTimescaleIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Alarme de fonte velha (V29): olha a leitura ACEITA, não a execução. */
class SourceFreshnessIntegrationTest extends AbstractTimescaleIntegrationTest {

    @Autowired SourceFreshnessService service;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM alert WHERE kind = 'SOURCE_STALE'");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED', 'APAC_METEO24H', 'TABUAMARE_PE02')");
    }

    @Test
    void recenteEmDia_paradaVira_stale_semNadaAceitoVira_noData_eTabuaCurtaVira_coverageLow() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        UUID live = ObservationFixtures.run(jdbc, "CEMADEN");
        ObservationFixtures.station(jdbc, "CEMADEN", "S1", -8.0, -34.9);
        ObservationFixtures.precipitation(jdbc, live, "CEMADEN", "S1", now.minus(Duration.ofMinutes(20)), 0.0);   // 90 min: ok
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        ObservationFixtures.station(jdbc, "CEMADEN_PED", "P1", -8.0, -34.9);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "P1", now.minus(Duration.ofHours(5)), 0.0);   // 180 min: parada
        UUID tide = ObservationFixtures.run(jdbc, "TABUAMARE_PE02");
        jdbc.update("INSERT INTO observation (source, station_id, variable, resolution, ts_utc, value, unit, interval_start_utc, "
                + "run_id, raw_hash, rule_version, value_hash, origin_ts_norm) VALUES ('TABUAMARE_PE02', 'pe02', 'tide_height', "
                + "'EVENT', ?::timestamptz, 1.5, 'm', ?::timestamptz, ?, repeat('a',64), 't', repeat('b',64), 'x')",
                now.plus(Duration.ofDays(10)).toString(), now.plus(Duration.ofDays(10)).toString(), tide);  // 10 dias à frente < 30

        Map<String, Freshness> f = service.freshness();
        assertThat(f.get("CEMADEN").state()).isEqualTo("FRESH");
        assertThat(f.get("CEMADEN_PED").state()).isEqualTo("STALE");
        assertThat(f.get("CEMADEN_PED").ageMinutes()).isBetween(299L, 301L);
        assertThat(f.get("APAC_METEO24H").state()).isEqualTo("NO_DATA");      // roda e só quarentena: parada
        assertThat(f.get("TABUAMARE_PE02").state()).isEqualTo("COVERAGE_LOW");
        assertThat(f.get("CKAN_RECIFE").state()).isEqualTo("UNMONITORED");

        int opened = service.raiseAlerts();
        assertThat(opened).isGreaterThanOrEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT severity FROM alert WHERE kind = 'SOURCE_STALE' AND station_id = 'CEMADEN_PED'",
                String.class)).isEqualTo("CRITICAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM alert WHERE kind = 'SOURCE_STALE' AND station_id = 'CEMADEN'",
                Integer.class)).isZero();
        assertThat(service.raiseAlerts()).isZero();                            // um alerta por episódio
    }
}
