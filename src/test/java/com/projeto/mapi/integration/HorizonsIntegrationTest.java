package com.projeto.mapi.integration;

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
import java.time.format.DateTimeFormatter;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /api/horizontes/{slug} (bloco D1): três camadas sem combinar, dia de risco ausente por "não validado". */
class HorizonsIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired OfficialForecastService forecasts;
    @MockitoBean InmetWarningClient inmetClient;

    @BeforeEach
    void seed() throws Exception {
        jdbc.update("DELETE FROM official_forecast");
        jdbc.update("INSERT INTO flood_points (name, slug, latitude, longitude, municipality, active) "
                + "SELECT 'Ponto H', 'HZ_PONTO', -8.05, -34.95, 'Recife', true "
                + "WHERE NOT EXISTS (SELECT 1 FROM flood_points WHERE slug = 'HZ_PONTO')");
        String s;
        try (var in = getClass().getResourceAsStream("/inmet/previsao-2611606-real.json")) {
            s = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        LocalDate today = LocalDate.now(ZoneId.of("America/Recife"));
        DateTimeFormatter f = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        for (int d = 4; d >= 0; d--) s = s.replace(LocalDate.of(2026, 11, 22).plusDays(d).format(f), "§" + today.plusDays(d).format(f));
        String body = s.replace("§", "").replaceFirst("\\{", "{\"_teste\": \"" + java.util.UUID.randomUUID() + "\",");
        when(inmetClient.fetchForecastJson(anyString())).thenAnswer(inv ->
                body.replace("\"2611606\"", "\"" + inv.getArgument(0) + "\""));
        forecasts.syncAll();
    }

    @Test
    void tresCamadasSemCombinar_diaDeRiscoNaoValidado_previsaoSoHojeEAmanha() throws Exception {
        mockMvc.perform(as(get("/api/horizontes/HZ_PONTO"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ibge").value("2611606"))
                .andExpect(jsonPath("$.official.origin").value("INMET"))
                .andExpect(jsonPath("$.official.forecast.days.length()").value(2))
                .andExpect(jsonPath("$.official.forecast.days[0].periods[0].period").value("MANHA"))
                .andExpect(jsonPath("$.official.forecast.days[0].periods[0].firstSeenAt").exists())
                .andExpect(jsonPath("$.dayRisk.available").value(false))
                .andExpect(jsonPath("$.dayRisk.reason").value("NAO_VALIDADO"))
                .andExpect(jsonPath("$.nextHour.origin").value("MAPi"))
                .andExpect(jsonPath("$.nextHour.level").value("SEM_DADO"))
                .andExpect(jsonPath("$.nextHour.reason").exists());
    }

    @Test
    void modoSombraEscondeONivelDeQuemNaoEAdmin_eExigeLogin() throws Exception {
        mockMvc.perform(as(get("/api/horizontes/HZ_PONTO"), userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextHour.inTest").value(true))
                .andExpect(jsonPath("$.nextHour.level").doesNotExist())
                .andExpect(jsonPath("$.official.forecast.days.length()").value(2));
        mockMvc.perform(get("/api/horizontes/HZ_PONTO")).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/horizontes/NAO_EXISTE"), userToken())).andExpect(status().isNotFound());
    }
}
