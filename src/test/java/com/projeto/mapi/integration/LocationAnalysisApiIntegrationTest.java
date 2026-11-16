package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.dto.CurrentWeatherDTO;
import com.projeto.mapi.dto.WeatherResponseDTO;
import com.projeto.mapi.service.terrain.LocalElevationService;
import com.projeto.mapi.service.weather.WeatherService;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import com.projeto.mapi.support.ObservationFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NC-BACK B7: análise de local (MEASURED, GAP sem estimativa, ESTIMATED sem estação, 400). */
class LocationAnalysisApiIntegrationTest extends AbstractApiIntegrationTest {

    private static final String ATTR = "DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC";
    private static final Instant AT = Instant.parse("2026-11-08T14:00:00Z");
    private static final String URL = "/api/locations/analysis?lat=-8.0&lon=-34.9&at=" + AT;

    @Autowired JdbcTemplate jdbc;
    @Autowired CacheManager cacheManager;
    @MockitoBean WeatherService weather;
    @MockitoBean LocalElevationService elevation;

    @BeforeEach
    void clean() {
        cacheManager.getCache("locationAnalysis").clear();
        jdbc.update("DELETE FROM station_canonical");
        jdbc.update("DELETE FROM station_month_check WHERE source = 'CEMADEN_PED'");
        jdbc.update("DELETE FROM observation WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        jdbc.update("DELETE FROM station WHERE source IN ('CEMADEN', 'CEMADEN_PED')");
        doReturn(new WeatherResponseDTO(-8.0, -34.9, 1, 5.0,
                new CurrentWeatherDTO("2026-11-08T11:00", 27.1, 76, 29, 1010.0, 1, 1, 1.5, 14.0, 300.0)))
                .when(weather).getWeatherData(anyDouble(), anyDouble());
        doReturn(OptionalDouble.empty()).when(elevation).mdtElevationAt(anyDouble(), anyDouble());
    }

    private void station(String id, String name, double lat, double lon) {
        ObservationFixtures.station(jdbc, "CEMADEN_PED", id, lat, lon);
        jdbc.update("UPDATE station SET name = ? WHERE source = 'CEMADEN_PED' AND station_id = ?", name, id);
    }

    private void check(String id, String status) {
        jdbc.update("INSERT INTO station_month_check (source, station_id, month_start_utc, status, reasons, hours_in_month, "
                + "hours_with_value, coverage, n_neighbors, rule_version) VALUES ('CEMADEN_PED', ?, '2026-11-01T00:00:00Z', ?, "
                + "'{}'::text[], 720, 700, 0.97, 3, 'chuva-ponto-v4')", id, status);
    }

    private JsonNode call(String url, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(as(get(url), userToken())).andExpect(status().is(expected)).andReturn()
                .getResponse().getContentAsString());
    }

    private void unify() throws Exception {
        mockMvc.perform(as(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/admin/stations/unify"), adminToken())).andExpect(status().isOk());
    }

    @Test
    void measured_maiorValorEntreValidas_terrenoEClima() throws Exception {
        station("PK1", "Pico", -8.0, -34.9);
        station("N1", "Vizinha 1", -8.005, -34.9);
        station("FAR", "Longe", -8.2, -34.9);                    // > 5 km: fora do raio
        unify();
        check("PK1", "PASS");
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "PK1", AT, 3.2);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N1", AT, 1.0);
        doReturn(OptionalDouble.of(4.2)).when(elevation).mdtElevationAt(anyDouble(), anyDouble());

        JsonNode r = call(URL, 200);
        assertThat(r.get("inArea").asBoolean()).isTrue();
        assertThat(r.get("isSea").asBoolean()).isFalse();
        assertThat(r.get("lat").asDouble()).isEqualTo(-8.0);                   // eco da coordenada pedida
        assertThat(call("/api/locations/analysis?lat=-8.0005&lon=-34.9&at=" + AT, 200).get("lat").asDouble()).isEqualTo(-8.0005); // mesma célula, outro eco
        JsonNode rain = r.get("rain");
        assertThat(rain.get("mode").asText()).isEqualTo("MEASURED");
        assertThat(rain.get("nowMmH").asDouble()).isEqualTo(3.2);
        assertThat(rain.get("station").get("stationId").asText()).isEqualTo("PK1");
        assertThat(rain.get("nValid").asInt()).isEqualTo(2);
        assertThat(rain.get("acc3hMm").isNull()).isTrue();                     // faltam horas
        assertThat(rain.get("candidates")).hasSize(2);
        assertThat(rain.get("candidates").get(0).get("role").asText()).isEqualTo("USED");
        assertThat(rain.get("candidates").get(1).get("role").asText()).isEqualTo("LOWER");
        assertThat(r.get("terrain").get("elevationM").asDouble()).isEqualTo(4.2);
        assertThat(r.get("weather").get("temperatureC").asDouble()).isEqualTo(27.1);
        assertThat(r.get("river").isNull()).isTrue();
        assertThat(r.get("attribution").asText()).isEqualTo(ATTR);
    }

    @Test
    void picoIsolado_naoEscolheUSED_perdeParaASegunda() throws Exception {
        station("PK1", "Pico", -8.0, -34.9);
        station("N1", "Vizinha 1", -8.005, -34.9);
        station("N2", "Vizinha 2", -8.0, -34.905);
        unify();
        UUID ped = ObservationFixtures.run(jdbc, "CEMADEN_PED");
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "PK1", AT, 12.0);   // >= 10 mm, vizinhas secas
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N1", AT, 0.4);
        ObservationFixtures.precipitation(jdbc, ped, "CEMADEN_PED", "N2", AT, 0.0);
        JsonNode rain = call(URL, 200).get("rain");
        assertThat(rain.get("mode").asText()).isEqualTo("MEASURED");
        assertThat(rain.get("nowMmH").asDouble()).isEqualTo(0.4);
        assertThat(rain.get("station").get("stationId").asText()).isEqualTo("N1");
        JsonNode pk1 = rain.get("candidates").get(0);          // a mais próxima (distância zero)
        assertThat(pk1.get("stationId").asText()).isEqualTo("PK1");
        assertThat(pk1.get("valueMmH").isNull()).isTrue();
        assertThat(pk1.get("role").asText()).isEqualTo("NO_VALUE");
        assertThat(pk1.get("reasons").toString()).isEqualTo("[\"ISOLATED_PEAK\"]");
        for (JsonNode c : rain.get("candidates")) {
            if (c.get("stationId").asText().equals("N1")) assertThat(c.get("role").asText()).isEqualTo("USED");
        }
    }

    @Test
    void gap_estacaoValidaSemValor_naoEstima() throws Exception {
        station("PK1", "Pico", -8.0, -34.9);
        unify();
        check("PK1", "PASS");
        JsonNode rain = call(URL, 200).get("rain");
        assertThat(rain.get("mode").asText()).isEqualTo("GAP");
        assertThat(rain.get("nowMmH").isNull()).isTrue();                      // o Open-Meteo diria 1,5: não entra
        assertThat(rain.get("acc3hMm").isNull()).isTrue();
        assertThat(rain.get("station").isNull()).isTrue();
        assertThat(rain.get("candidates").get(0).get("role").asText()).isEqualTo("NO_VALUE");
    }

    @Test
    void estimated_semEstacaoValida_usaOpenMeteo_eMarSemMdt() throws Exception {
        station("BAD", "Reprovada", -8.0, -34.9);                              // só há uma, e reprovada
        unify();
        check("BAD", "FAIL");
        doReturn(new WeatherResponseDTO(-8.0, -34.9, 1, 0.0,
                new CurrentWeatherDTO("2026-11-08T11:00", 27.1, 76, 29, 1010.0, 1, 1, 1.5, 14.0, 300.0)))
                .when(weather).getWeatherData(anyDouble(), anyDouble());
        JsonNode r = call(URL, 200);
        JsonNode rain = r.get("rain");
        assertThat(rain.get("mode").asText()).isEqualTo("ESTIMATED");
        assertThat(rain.get("nowMmH").asDouble()).isEqualTo(1.5);
        assertThat(rain.get("station").isNull()).isTrue();
        assertThat(rain.get("nValid").asInt()).isZero();
        assertThat(rain.get("candidates").get(0).get("role").asText()).isEqualTo("EXCLUDED");
        assertThat(r.get("isSea").asBoolean()).isTrue();                       // fora do MDT e altitude 0
        assertThat(r.get("terrain").isNull()).isTrue();

        JsonNode fora = call("/api/locations/analysis?lat=-9.5&lon=-40.0&at=" + AT, 200);   // fora da RMR
        assertThat(fora.get("inArea").asBoolean()).isFalse();
        assertThat(fora.get("rain").get("mode").asText()).isEqualTo("ESTIMATED");
        assertThat(fora.get("terrain").isNull()).isTrue();
    }

    @Test
    void coordenadaInvalida_da400() throws Exception {
        call("/api/locations/analysis?lat=95&lon=-34.9", 400);
        call("/api/locations/analysis?lat=-8.0&lon=181", 400);
        call("/api/locations/analysis?lat=abc&lon=-34.9", 400);
        call("/api/locations/analysis?lon=-34.9", 400);
    }
}
