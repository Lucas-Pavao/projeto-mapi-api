package com.projeto.mapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.FloodEventRepository;
import com.projeto.mapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** POST /align-events continua existindo, mas responde 410 Gone e não toca nos eventos. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AlignEventsGoneTest {

    private static final String PASSWORD = "SenhaDeTeste#123";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;
    @MockitoSpyBean private FloodEventRepository floodEventRepository;

    @Test
    void alignEvents_retorna410ExplicandoOMotivoENaoMexeNosEventos() throws Exception {
        if (userRepository.findByUsername("align_admin_test").isEmpty()) {
            userRepository.save(User.builder().username("align_admin_test")
                    .password(passwordEncoder.encode(PASSWORD)).role(Role.ADMIN).build());
        }
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "align_admin_test", "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(post("/api/admin/ingestion/align-events").header("Authorization", "Bearer " + token))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.statusCode").value(410))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("ADR 0006")));

        verifyNoMoreInteractions(floodEventRepository);
    }
}
