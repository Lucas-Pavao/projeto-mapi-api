package com.projeto.mapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auxiliar dos testes do wipe-database: cria um ADMIN e devolve o token JWT dele. */
abstract class WipeDatabaseTestSupport {

    private static final String PASSWORD = "SenhaDeTeste#123";

    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    protected String adminToken(MockMvc mockMvc) throws Exception {
        if (userRepository.findByUsername("wipe_admin_test").isEmpty()) {
            userRepository.save(User.builder().username("wipe_admin_test")
                    .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).build());
        }
        String body = objectMapper.writeValueAsString(Map.of("username", "wipe_admin_test", "password", PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }
}
