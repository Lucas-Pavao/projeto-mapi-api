package com.projeto.mapi.integration;

import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** ADR 0010, grupo 1: matriz de autorização de /api/admin/users e /api/admin/invites, convite e desativação. */
class AdminUsersAuthorizationIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void listarUsuarios_soAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), userToken())).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), operadorToken())).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0].email").exists())
                .andExpect(jsonPath("$.content[0].active").value(true));
    }

    @Test
    void criarConvite_soAdmin() throws Exception {
        String body = "{\"role\":\"OPERADOR\"}";
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(post("/api/admin/invites"), t).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(as(post("/api/admin/invites"), adminToken()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("OPERADOR"))
                .andExpect(jsonPath("$.expiresAt").isNotEmpty());
    }

    @Test
    void patchUsuario_soAdmin() throws Exception {
        User alvo = userRepository.findByUsername("it_patch_alvo").orElseGet(() -> userRepository.save(User.builder()
                .username("it_patch_alvo").password("x").role(Role.USER).build()));
        String body = "{\"role\":\"OPERADOR\"}";
        for (String t : new String[]{null, userToken(), operadorToken()}) {
            mockMvc.perform(as(patch("/api/admin/users/" + alvo.getId()), t)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        mockMvc.perform(as(patch("/api/admin/users/" + alvo.getId()), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OPERADOR"));
    }

    @Test
    void adminNaoAlteraASiMesmo_409() throws Exception {
        String token = adminToken();
        Long id = userRepository.findByUsername("it_admin").orElseThrow().getId();
        mockMvc.perform(as(patch("/api/admin/users/" + id), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}")).andExpect(status().isConflict());
    }

    @Test
    void patchSemCampos_400_eUsuarioInexistente_404() throws Exception {
        String token = adminToken();
        Long id = userRepository.findByUsername("it_user").orElseGet(() -> userRepository.findByUsername("it_admin").orElseThrow()).getId();
        mockMvc.perform(as(patch("/api/admin/users/" + id), token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(as(patch("/api/admin/users/999999"), token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"active\":true}")).andExpect(status().isNotFound());
    }

    @Test
    void conviteDefinePerfil_usoUnico_eVence() throws Exception {
        String resp = mockMvc.perform(as(post("/api/admin/invites"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OPERADOR\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("token").asText();

        String reg = "{\"username\":\"convidado_a\",\"password\":\"SenhaForte#123\",\"inviteToken\":\"" + token + "\"}";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByUsername("convidado_a").orElseThrow().getRole()).isEqualTo(Role.OPERADOR);

        // segundo uso do mesmo token
        String reg2 = reg.replace("convidado_a", "convidado_b");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(reg2))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername("convidado_b")).isEmpty();

        // convite vencido
        String resp2 = mockMvc.perform(as(post("/api/admin/invites"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andReturn().getResponse().getContentAsString();
        String token2 = objectMapper.readTree(resp2).get("token").asText();
        jdbc.update("UPDATE user_invite SET expires_at = now() - interval '1 minute' WHERE used_at IS NULL");
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"convidado_c\",\"password\":\"SenhaForte#123\",\"inviteToken\":\"" + token2 + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroSemConvite_viraUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sem_convite\",\"password\":\"SenhaForte#123\"}")).andExpect(status().isCreated());
        assertThat(userRepository.findByUsername("sem_convite").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void desativarUsuario_bloqueiaLoginETokenERevogaRefresh() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"para_desativar\",\"password\":\"SenhaForte#123\"}")).andExpect(status().isCreated());
        Long id = userRepository.findByUsername("para_desativar").orElseThrow().getId();
        String loginBody = "{\"username\":\"para_desativar\",\"password\":\"SenhaForte#123\"}";
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(as(get("/api/auth/me"), access)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?", Integer.class, id)).isEqualTo(1);

        mockMvc.perform(as(patch("/api/admin/users/" + id), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?", Integer.class, id)).isZero();
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/auth/me"), access)).andExpect(status().isForbidden());
    }

    @Test
    void checkConstraintAceitaOperador() {
        jdbc.update("INSERT INTO users (username, password, role) VALUES ('it_check_op', 'x', 'OPERADOR') ON CONFLICT DO NOTHING");
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username='it_check_op'", String.class)).isEqualTo("OPERADOR");
    }
}
