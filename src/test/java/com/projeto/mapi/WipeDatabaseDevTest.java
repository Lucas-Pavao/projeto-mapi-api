package com.projeto.mapi;

import com.projeto.mapi.service.sensor.HistoricalDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Com o profile "dev" o endpoint existe, exige ADMIN e chega ao serviço (mockado: nada é apagado). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "dev"})
class WipeDatabaseDevTest extends WipeDatabaseTestSupport {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HistoricalDataService historicalDataService;

    @Test
    void comProfileDev_adminChegaAoServico() throws Exception {
        String token = adminToken(mockMvc);
        mockMvc.perform(delete("/api/admin/ingestion/wipe-database")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(historicalDataService).wipeDatabase();
    }

    @Test
    void comProfileDev_semTokenContinuaProibido() throws Exception {
        mockMvc.perform(delete("/api/admin/ingestion/wipe-database"))
                .andExpect(status().isForbidden());
    }
}
