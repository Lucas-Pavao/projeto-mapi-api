package com.projeto.mapi.integration;

import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;
import com.projeto.mapi.dto.OfficialForecastDTO;
import com.projeto.mapi.service.ingestion.freshness.SourceFreshnessService;
import com.projeto.mapi.service.warning.InmetWarningClient;
import com.projeto.mapi.service.warning.OfficialForecastService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Previsão oficial do INMET (V41): bruto com hash, uma linha por versão, first_seen_at preservado, frescor e rotas. */
class OfficialForecastIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired OfficialForecastService service;
    @Autowired SourceFreshnessService freshnessService;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean InmetWarningClient inmetClient;

    @BeforeEach
    void clean() {
        // raw_observation e ingestion_run são só-inserção (ADR 0006): as contagens partem de uma linha de base.
        jdbc.update("DELETE FROM official_forecast");
        marker = java.util.UUID.randomUUID().toString();
    }

    private String marker;

    private int rawCount() {
        return jdbc.queryForObject("SELECT count(*) FROM raw_observation WHERE source = 'INMET_PREVISAO'", Integer.class);
    }

    /** A fixture real é de 22/11/2026; troca as datas para hoje em diante, para a leitura de "vigente". */
    private String fixtureForToday(String ibge) throws Exception {
        String s;
        try (var in = getClass().getResourceAsStream("/inmet/previsao-2611606-real.json")) {
            s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        LocalDate today = LocalDate.now(ZoneId.of("America/Recife"));
        java.time.format.DateTimeFormatter f = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (int d = 4; d >= 0; d--) {
            s = s.replace(LocalDate.of(2026, 11, 22).plusDays(d).format(f), "§" + today.plusDays(d).format(f));
        }
        // marcador fora do município: muda o hash do bruto entre testes sem mudar a previsão
        return s.replace("§", "").replace("\"2611606\"", "\"" + ibge + "\"").replaceFirst("\\{", "{\"_teste\": \"" + marker + "\",");
    }

    @Test
    void coletaGravaBrutoEVersoes_revisitarSoAvancaLastSeen_eMudancaViraLinhaNova() throws Exception {
        when(inmetClient.fetchForecastJson(anyString())).thenAnswer(inv -> fixtureForToday(inv.getArgument(0)));

        int raw0 = rawCount();
        int first = service.syncAll();
        assertThat(first).isEqualTo(27);                       // 3 municípios x 9 entradas
        assertThat(rawCount() - raw0).isEqualTo(3);

        int again = service.syncAll();
        assertThat(again).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM official_forecast", Integer.class)).isEqualTo(27);
        assertThat(rawCount() - raw0).isEqualTo(3);            // corpo igual: bruto não se repete
        assertThat(jdbc.queryForObject("SELECT count(*) FROM official_forecast WHERE last_seen_at > first_seen_at", Integer.class)).isEqualTo(27);

        when(inmetClient.fetchForecastJson(anyString())).thenAnswer(inv ->
                fixtureForToday(inv.getArgument(0)).replaceFirst("Poucas nuvens", "Pancadas de chuva"));
        assertThat(service.syncAll()).isEqualTo(3);            // uma versão nova por município; a antiga fica
        assertThat(jdbc.queryForObject("SELECT count(*) FROM official_forecast", Integer.class)).isEqualTo(30);

        OfficialForecastDTO dto = service.current("Recife");
        assertThat(dto.reason()).isNull();
        assertThat(dto.stale()).isFalse();
        assertThat(dto.days()).hasSize(5);
        assertThat(dto.days().get(0).periods().get(0).summary()).isEqualTo("Pancadas de chuva");

        Freshness f = freshnessService.freshness().get("INMET_PREVISAO");
        assertThat(f).isNotNull();
        assertThat(f.state()).isEqualTo("FRESH");
        assertThat(f.maxAgeMinutes()).isEqualTo(720);
    }

    @Test
    void falhaDeUmMunicipioViraParcialSemDerrubarOsOutros() throws Exception {
        when(inmetClient.fetchForecastJson(anyString())).thenAnswer(inv -> {
            String ibge = inv.getArgument(0);
            if ("2607901".equals(ibge)) throw new IllegalStateException("fora do ar");
            return fixtureForToday(ibge);
        });
        assertThat(service.syncAll()).isEqualTo(18);
        assertThat(jdbc.queryForObject("SELECT status FROM ingestion_run WHERE source = 'INMET_PREVISAO' "
                + "ORDER BY started_at DESC LIMIT 1", String.class)).isEqualTo("PARTIAL");
        assertThat(service.current("2607901").reason()).isEqualTo("SEM_PREVISAO");
    }

    @Test
    void rotaDoAppExigeLogin_rotaPublicaNao_municipioDesconhecidoTemMotivo() throws Exception {
        when(inmetClient.fetchForecastJson(anyString())).thenAnswer(inv -> fixtureForToday(inv.getArgument(0)));
        service.syncAll();
        mockMvc.perform(get("/api/previsao-oficial/Recife")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/public/previsao-oficial/2611606"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("INMET"))
                .andExpect(jsonPath("$.days[0].periods[0].period").value("MANHA"))
                .andExpect(jsonPath("$.days[0].periods[0].firstSeenAt").exists());
        mockMvc.perform(get("/api/public/previsao-oficial/Caruaru"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reason").value("MUNICIPIO_DESCONHECIDO"))
                .andExpect(jsonPath("$.days").isEmpty());
    }
}
