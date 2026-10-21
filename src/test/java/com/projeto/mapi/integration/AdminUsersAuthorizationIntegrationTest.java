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

    private static int ipSeq = 0;

    /** O LoginRateLimitingFilter limita register/login a 5 por minuto por IP: cada chamada usa um IP próprio. */
    private static synchronized String ip() {
        return "10.8.0." + (++ipSeq);
    }

    @Test
    void listarUsuarios_soAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/users")).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), userToken())).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), operadorToken())).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.content[0]").value(org.hamcrest.Matchers.hasKey("email")))
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
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON).content(reg))
                .andExpect(status().isCreated());
        assertThat(userRepository.findByUsername("convidado_a").orElseThrow().getRole()).isEqualTo(Role.OPERADOR);

        // segundo uso do mesmo token
        String reg2 = reg.replace("convidado_a", "convidado_b");
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON).content(reg2))
                .andExpect(status().isBadRequest());
        assertThat(userRepository.findByUsername("convidado_b")).isEmpty();

        // convite vencido
        String resp2 = mockMvc.perform(as(post("/api/admin/invites"), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"ADMIN\"}"))
                .andReturn().getResponse().getContentAsString();
        String token2 = objectMapper.readTree(resp2).get("token").asText();
        jdbc.update("UPDATE user_invite SET expires_at = now() - interval '1 minute' WHERE used_at IS NULL");
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"convidado_c\",\"password\":\"SenhaForte#123\",\"inviteToken\":\"" + token2 + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cadastroSemConvite_viraUser() throws Exception {
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"sem_convite\",\"password\":\"SenhaForte#123\"}")).andExpect(status().isCreated());
        assertThat(userRepository.findByUsername("sem_convite").orElseThrow().getRole()).isEqualTo(Role.USER);
    }

    @Test
    void desativarUsuario_bloqueiaLoginETokenERevogaRefresh() throws Exception {
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"para_desativar\",\"password\":\"SenhaForte#123\"}")).andExpect(status().isCreated());
        Long id = userRepository.findByUsername("para_desativar").orElseThrow().getId();
        String loginBody = "{\"username\":\"para_desativar\",\"password\":\"SenhaForte#123\"}";
        String login = mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String access = "Bearer " + objectMapper.readTree(login).get("accessToken").asText();
        mockMvc.perform(as(get("/api/auth/me"), access)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?", Integer.class, id)).isEqualTo(1);

        mockMvc.perform(as(patch("/api/admin/users/" + id), adminToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?", Integer.class, id)).isZero();
        mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(as(get("/api/auth/me"), access)).andExpect(status().isForbidden());
    }

    @Test
    void checkConstraintAceitaOperador() {
        jdbc.update("INSERT INTO users (username, password, role) VALUES ('it_check_op', 'x', 'OPERADOR') ON CONFLICT DO NOTHING");
        assertThat(jdbc.queryForObject("SELECT role FROM users WHERE username='it_check_op'", String.class)).isEqualTo("OPERADOR");
    }

    // ---------- V19: e-mail e nome ----------

    @Test
    void cadastroGuardaEmailENome_minusculo_eRejeitaEmailRepetido() throws Exception {
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"com_email\",\"password\":\"SenhaForte#123\",\"email\":\"Maria@Exemplo.COM\",\"name\":\"Maria\"}"))
                .andExpect(status().isCreated());
        var u = userRepository.findByUsername("com_email").orElseThrow();
        assertThat(u.getEmail()).isEqualTo("maria@exemplo.com");
        assertThat(u.getName()).isEqualTo("Maria");

        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"outro_nome\",\"password\":\"SenhaForte#123\",\"email\":\"MARIA@exemplo.com\"}"))
                .andExpect(status().isConflict());
        assertThat(userRepository.findByUsername("outro_nome")).isEmpty();
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"mail_ruim\",\"password\":\"SenhaForte#123\",\"email\":\"nao-eh-email\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void conviteComEmailPreencheUsersEmail_eValeSobreOInformadoNoCadastro() throws Exception {
        String resp = mockMvc.perform(as(post("/api/admin/invites"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"OPERADOR\",\"email\":\"Operador.Convite@exemplo.com\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("token").asText();
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"op_convite\",\"password\":\"SenhaForte#123\",\"email\":\"outro@exemplo.com\",\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isCreated());
        var u = userRepository.findByUsername("op_convite").orElseThrow();
        assertThat(u.getRole()).isEqualTo(Role.OPERADOR);
        assertThat(u.getEmail()).isEqualTo("operador.convite@exemplo.com");
    }

    @Test
    void emailRepetidoNoCadastroComConviteDevolveOConvite() throws Exception {
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"dono_email\",\"password\":\"SenhaForte#123\",\"email\":\"repetido@exemplo.com\"}"))
                .andExpect(status().isCreated());
        String resp = mockMvc.perform(as(post("/api/admin/invites"), adminToken()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"USER\",\"email\":\"repetido@exemplo.com\"}"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(resp).get("token").asText();
        mockMvc.perform(post("/api/auth/register").header("X-Forwarded-For", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"tentativa\",\"password\":\"SenhaForte#123\",\"inviteToken\":\"" + token + "\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT used_at IS NULL FROM user_invite WHERE token_hash = ?", Boolean.class,
                com.projeto.mapi.service.ingestion.Hashing.sha256(token))).as("rollback devolve o convite").isTrue();
    }

    @Test
    void adminEditaEmailENome_umPorUsuario_eAteOProprio() throws Exception {
        User a = userRepository.findByUsername("it_edit_a").orElseGet(() -> userRepository.save(User.builder()
                .username("it_edit_a").password("x").role(Role.USER).build()));
        User b = userRepository.findByUsername("it_edit_b").orElseGet(() -> userRepository.save(User.builder()
                .username("it_edit_b").password("x").role(Role.USER).build()));
        String t = adminToken();
        mockMvc.perform(as(patch("/api/admin/users/" + a.getId()), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"A@Edit.com\",\"name\":\"Ana\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("a@edit.com")).andExpect(jsonPath("$.name").value("Ana"));
        mockMvc.perform(as(patch("/api/admin/users/" + b.getId()), t).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@edit.com\"}"))
                .andExpect(status().isConflict());
        // limpar com string vazia
        mockMvc.perform(as(patch("/api/admin/users/" + a.getId()), t).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").isEmpty());
        // o ADMIN pode editar o próprio nome, mas não o próprio perfil
        Long self = userRepository.findByUsername("it_admin").orElseThrow().getId();
        mockMvc.perform(as(patch("/api/admin/users/" + self), t).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Admin IT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Admin IT"));
        mockMvc.perform(as(patch("/api/admin/users/" + self), t).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isConflict());
        // email mal formado
        mockMvc.perform(as(patch("/api/admin/users/" + a.getId()), t).contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
