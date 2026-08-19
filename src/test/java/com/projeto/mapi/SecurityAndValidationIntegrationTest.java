package com.projeto.mapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testes de regressão para os bugs de segurança/validação corrigidos na auditoria: acesso
 * indevido a endpoints administrativos, 500 em vez de 400/404 para entrada inválida, etc.
 * Sobe o contexto Spring real (perfil "test", H2 em memória) para exercitar a cadeia de
 * segurança (JwtAuthenticationFilter + SecurityConfig) exatamente como em produção.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityAndValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PASSWORD = "SenhaDeTeste#123";

    @BeforeEach
    void setUp() {
        userRepository.findByUsername("regular_user_test").ifPresentOrElse(u -> {}, () ->
                userRepository.save(User.builder()
                        .username("regular_user_test")
                        .password(passwordEncoder.encode(PASSWORD))
                        .role(Role.USER)
                        .build()));

        userRepository.findByUsername("admin_user_test").ifPresentOrElse(u -> {}, () ->
                userRepository.save(User.builder()
                        .username("admin_user_test")
                        .password(passwordEncoder.encode(PASSWORD))
                        .role(Role.ADMIN)
                        .build()));
    }

    private String loginAndGetToken(String username) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginPayload(username, PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    @Test
    void adminIngestionEndpoint_withoutToken_isRejected() throws Exception {
        mockMvc.perform(get("/api/admin/ingestion/check-integrity"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIngestionEndpoint_withRegularUserToken_isForbidden() throws Exception {
        String token = loginAndGetToken("regular_user_test");
        mockMvc.perform(get("/api/admin/ingestion/check-integrity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminIngestionEndpoint_withAdminToken_isAllowed() throws Exception {
        String token = loginAndGetToken("admin_user_test");
        mockMvc.perform(get("/api/admin/ingestion/check-integrity")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void weatherEndpoint_withoutRequiredParams_returns400NotServerError() throws Exception {
        mockMvc.perform(get("/api/weather"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.statusCode").value(400));
    }

    @Test
    void floodPointCreation_withMissingRequiredFields_returns400WithFieldErrors() throws Exception {
        String token = loginAndGetToken("regular_user_test");
        mockMvc.perform(post("/api/pontos")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void floodEventHistory_forUnknownSlug_returns404NotServerError() throws Exception {
        String token = loginAndGetToken("regular_user_test");
        mockMvc.perform(get("/api/eventos-alagamento/ponto-que-nao-existe-xyz")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private record LoginPayload(String username, String password) {}
}
