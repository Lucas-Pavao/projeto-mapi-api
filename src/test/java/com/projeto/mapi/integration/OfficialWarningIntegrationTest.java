package com.projeto.mapi.integration;

import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.service.ingestion.freshness.SourceFreshnessService;
import com.projeto.mapi.service.warning.InmetWarningClient;
import com.projeto.mapi.service.warning.InmetWarningParser;
import com.projeto.mapi.service.warning.OfficialWarningService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Integração dos avisos oficiais do INMET (V31, ADR 0024): persistência, proxy de emissão, frescor e rota REST. */
class OfficialWarningIntegrationTest extends AbstractApiIntegrationTest {

    private static final DateTimeFormatter BR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Autowired OfficialWarningService service;
    @Autowired SourceFreshnessService freshnessService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean InmetWarningClient inmetClient;

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM official_warning");
        jdbc.update("DELETE FROM raw_observation WHERE source = 'INMET_AVISOS'");
        jdbc.update("DELETE FROM ingestion_run WHERE source = 'INMET_AVISOS'");
    }

    private String fixture(String name) throws Exception {
        try (var in = getClass().getResourceAsStream("/inmet/" + name)) {
            assertThat(in).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void coletaRealSemRmr_registraRunFresh_eMantemTabelaVazia() throws Exception {
        when(inmetClient.fetchActiveJson()).thenReturn(fixture("avisos-ativos-real.json"));

        int persisted = service.syncInmetActive();
        assertThat(persisted).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM official_warning", Integer.class)).isZero();

        Freshness f = freshnessService.freshness().get("INMET_AVISOS");
        assertThat(f).isNotNull();
        assertThat(f.state()).isEqualTo("FRESH");
        assertThat(f.maxAgeMinutes()).isEqualTo(30);
    }

    @Test
    void coletaComRecife_preservaFirstSeenAt_filtraPorMunicipio_eExigeAuth() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MINUTES);
        String inicio = BR_FMT.format(now.minus(1, ChronoUnit.HOURS).atZone(InmetWarningParser.ZONE));
        String fim = BR_FMT.format(now.plus(6, ChronoUnit.HOURS).atZone(InmetWarningParser.ZONE));
        String payload = fixture("avisos-ativos-recife.json")
                .replace("2026-11-16 09:05", inicio)
                .replace("2026-11-16 23:59", fim);
        when(inmetClient.fetchActiveJson()).thenReturn(payload);

        int firstCount = service.syncInmetActive();
        assertThat(firstCount).isEqualTo(1);

        OffsetDateTime firstSeen1 = jdbc.queryForObject(
                "SELECT first_seen_at FROM official_warning WHERE warning_id = 28712", OffsetDateTime.class);
        OffsetDateTime lastSeen1 = jdbc.queryForObject(
                "SELECT last_seen_at FROM official_warning WHERE warning_id = 28712", OffsetDateTime.class);
        assertThat(firstSeen1).isNotNull();
        assertThat(lastSeen1).isEqualTo(firstSeen1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM raw_observation WHERE source = 'INMET_AVISOS'", Integer.class)).isEqualTo(1);

        Thread.sleep(20);
        service.syncInmetActive();

        OffsetDateTime firstSeen2 = jdbc.queryForObject(
                "SELECT first_seen_at FROM official_warning WHERE warning_id = 28712", OffsetDateTime.class);
        OffsetDateTime lastSeen2 = jdbc.queryForObject(
                "SELECT last_seen_at FROM official_warning WHERE warning_id = 28712", OffsetDateTime.class);
        assertThat(firstSeen2).isEqualTo(firstSeen1);
        assertThat(lastSeen2).isAfter(lastSeen1);
        // Hash idêntico na segunda visita: não duplica bruto em raw_observation
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM raw_observation WHERE source = 'INMET_AVISOS'", Integer.class)).isEqualTo(1);

        // Anônimo bloqueado (403)
        mockMvc.perform(get("/api/warnings/official/active")).andExpect(status().isForbidden());

        // Consulta sem filtro e filtrada por Recife / IBGE de Jaboatão / município fora
        mockMvc.perform(as(get("/api/warnings/official/active"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("INMET"))
                .andExpect(jsonPath("$.emissionNote").value(org.hamcrest.Matchers.containsString("proxy")))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.warnings[0].warningId").value(28712))
                .andExpect(jsonPath("$.warnings[0].colorHex").value("#F96602"));

        mockMvc.perform(as(get("/api/warnings/official/active").param("municipio", "Recife"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(1));

        mockMvc.perform(as(get("/api/warnings/official/active").param("municipio", "2607901"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(1));

        mockMvc.perform(as(get("/api/warnings/official/active").param("municipio", "Petrolina"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(0));
    }
}
