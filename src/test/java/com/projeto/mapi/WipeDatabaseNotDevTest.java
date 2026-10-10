package com.projeto.mapi;

import com.projeto.mapi.service.sensor.HistoricalDataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sem o profile "dev", DELETE /wipe-database não existe: mesmo ADMIN recebe 404 e o serviço não é tocado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WipeDatabaseNotDevTest extends WipeDatabaseTestSupport {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private HistoricalDataService historicalDataService;

    @Test
    void foraDoProfileDev_retorna404ENaoApagaNada() throws Exception {
        String token = adminToken(mockMvc);
        mockMvc.perform(delete("/api/admin/ingestion/wipe-database")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        verify(historicalDataService, never()).wipeDatabase();
    }
}
