package com.projeto.mapi.integration;

import com.projeto.mapi.service.ingestion.Hashing;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Grupo 7: GET /api/model/evaluation (perfil mínimo USER). O 503 por divergência está no teste unitário do serviço. */
class ModelEvaluationIntegrationTest extends AbstractApiIntegrationTest {

    static final String JSON = "{\"auc_B2\": 0.61, \"p\": 1e-05}";
    static Path file;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) throws Exception {
        file = Files.createTempFile("resultado_etapa2", ".json");
        Files.writeString(file, JSON);
        r.add("mapi.model.evaluation.path", file::toString);
        r.add("mapi.model.evaluation.expected-sha256", () -> Hashing.sha256(JSON.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void anonimoNao_userEmaisSim_comHashNoCabecalho() throws Exception {
        mockMvc.perform(get("/api/model/evaluation")).andExpect(status().isForbidden());
        for (String t : new String[]{userToken(), operadorToken(), adminToken()}) {
            String body = mockMvc.perform(as(get("/api/model/evaluation"), t))
                    .andExpect(status().isOk())
                    .andExpect(header().string("X-Content-SHA256", Hashing.sha256(JSON.getBytes(StandardCharsets.UTF_8))))
                    .andExpect(jsonPath("$.source").value("avaliacao-f1/resultado_etapa2.json"))
                    .andExpect(jsonPath("$.result.auc_B2").value(0.61))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as("número literal preservado").contains("1e-05");
        }
    }

    @Test
    void arquivoAdulteradoDepoisDaSubida_503() throws Exception {
        String original = Files.readString(file);
        try {
            Files.writeString(file, "{\"auc_B2\": 0.99}");
            mockMvc.perform(as(get("/api/model/evaluation"), userToken())).andExpect(status().isServiceUnavailable());
        } finally {
            Files.writeString(file, original);
        }
        mockMvc.perform(as(get("/api/model/evaluation"), userToken())).andExpect(status().isOk());
    }
}
