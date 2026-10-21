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
        LocalDateTime agora = LocalDateTime.now();
        sensorRepository.save(SensorData.builder().sensorId("APAC-PLUVIO-T1").value(2.0).unit("mm")
                .accumulatedPrecipitation(2.0).source("APAC/Cemaden").timestamp(agora.minusMinutes(1)).build());
        // Cemaden a cada 3 min: 10 min > 2 x 3 min => sem resposta
        sensorRepository.save(SensorData.builder().sensorId("APAC-PLUVIO-T2").value(0.0).unit("mm")
                .accumulatedPrecipitation(0.0).source("APAC/Cemaden").timestamp(agora.minusMinutes(10)).build());
        // ANA a cada 15 min: 20 min < 30 min => ok
        sensorRepository.save(SensorData.builder().sensorId("ANA-T3").value(1.5).unit("m")
                .waterLevel(1.5).source("ANA").timestamp(agora.minusMinutes(20)).build());
    }

    @Test
    void semFiltro_mantemCamposAntigosEAcrescentaFrescor() throws Exception {
        // público (security: [] no contrato)
        mockMvc.perform(get("/api/sensors/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
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
    void filtroPorType() throws Exception {
        mockMvc.perform(get("/api/sensors/latest?type=RIVER_LEVEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].sensorId").value("ANA-T3"));
        mockMvc.perform(get("/api/sensors/latest?type=PRECIPITATION"))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/sensors/latest?type=OUTRO")).andExpect(status().isBadRequest());
    }
}
