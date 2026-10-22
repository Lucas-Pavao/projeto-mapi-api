package com.projeto.mapi.integration;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** GET /api/sensors/latest: filtro por type e campos ageSeconds/stale (compatível com os campos antigos). */
class SensorsLatestFreshnessIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired SensorDataRepository sensorRepository;

    @BeforeEach
    void semeia() {
        sensorRepository.deleteAll();
        // sensor_data guarda a hora local da fonte (Recife), independente do fuso da JVM
        LocalDateTime agora = LocalDateTime.now(java.time.ZoneId.of("America/Recife"));
        sensorRepository.save(SensorData.builder().sensorId("APAC-PLUVIO-T1").value(2.0).unit("mm")
                .accumulatedPrecipitation(2.0).source("APAC/Cemaden").timestamp(agora.minusMinutes(1)).build());
        // Cemaden publica blocos de até 60 min: 3 h > 2 x 60 min => sem resposta
        sensorRepository.save(SensorData.builder().sensorId("APAC-PLUVIO-T2").value(0.0).unit("mm")
                .accumulatedPrecipitation(0.0).source("APAC/Cemaden").timestamp(agora.minusHours(3)).build());
        // Cemaden num bloco seco de 60 min, publicado há 70 min: ainda respondendo
        sensorRepository.save(SensorData.builder().sensorId("APAC-PLUVIO-T5").value(0.0).unit("mm")
                .accumulatedPrecipitation(0.0).source("APAC/Cemaden").timestamp(agora.minusMinutes(70)).build());
        // ANA publica com ~1 h de atraso: 20 min => ok
        sensorRepository.save(SensorData.builder().sensorId("ANA-T3").value(150.0).unit("mm")
                .waterLevel(150.0).source("ANA").timestamp(agora.minusMinutes(20)).build());
        // ANA sem chuva nem cota: sem tipo, fica fora do contrato
        sensorRepository.save(SensorData.builder().sensorId("ANA-T4").value(0.0).unit("mm")
                .source("ANA").timestamp(agora.minusMinutes(5)).build());
    }

    @Test
    void semFiltro_mantemCamposAntigosEAcrescentaFrescor() throws Exception {
        // público (security: [] no contrato)
        mockMvc.perform(get("/api/sensors/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T5')].stale").value(false))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T1')].stale").value(false))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T1')].value").value(2.0))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T1')].timestamp").isNotEmpty())
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T1')].type").value("PRECIPITATION"))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T2')].stale").value(true))
                .andExpect(jsonPath("$[?(@.sensorId=='APAC-PLUVIO-T2')].ageSeconds").isNotEmpty())
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].stale").value(false))
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].observedAt").isNotEmpty());
    }

    @Test
    void horaLocalDaFonte_cotaEmMetros_semTipoFicaDeFora() throws Exception {
        mockMvc.perform(get("/api/sensors/latest"))
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T4')]").isEmpty())
                // 20 min, não 3 h e 20 min: a hora local não é lida como UTC
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].ageSeconds").value(org.hamcrest.Matchers.contains(
                        org.hamcrest.Matchers.both(org.hamcrest.Matchers.greaterThanOrEqualTo(1190))
                                .and(org.hamcrest.Matchers.lessThan(1290)))))
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].value").value(1.5))
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].unit").value("m"))
                .andExpect(jsonPath("$[?(@.sensorId=='ANA-T3')].waterLevel").value(150.0));
    }

    @Test
    void filtroPorType() throws Exception {
        mockMvc.perform(get("/api/sensors/latest?type=RIVER_LEVEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sensorId").value("ANA-T3"));
        mockMvc.perform(get("/api/sensors/latest?type=PRECIPITATION"))
                .andExpect(jsonPath("$.length()").value(3));
        mockMvc.perform(get("/api/sensors/latest?type=OUTRO")).andExpect(status().isBadRequest());
    }
}
