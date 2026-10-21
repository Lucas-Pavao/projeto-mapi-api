package com.projeto.mapi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.config.JwtProperties;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.support.AbstractApiIntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** ADR 0010, emenda 2: o perfil viaja no token e a revogação é por tokenVersion. */
class TokenRevocationIntegrationTest extends AbstractApiIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProperties jwtProperties;

    private static int ipSeq = 0;

    private static synchronized String ip() {
        return "10.7.0." + (++ipSeq);
    }

    private String loginBody(String user) {
        return "{\"username\":\"" + user + "\",\"password\":\"" + SENHA + "\"}";
    }

    private JsonNode login(String user) throws Exception {
        String r = mockMvc.perform(post("/api/auth/login").header("X-Forwarded-For", ip())
                        .contentType(MediaType.APPLICATION_JSON).content(loginBody(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(r);
    }

    private JsonNode claims(String jwt) throws Exception {
        return objectMapper.readTree(Base64.getUrlDecoder().decode(jwt.split("\\.")[1]));
    }

    private Long id(String user) {
        return userRepository.findByUsername(user).orElseThrow().getId();
    }

    private void patchUser(String adminBearer, Long id, String body) throws Exception {
        mockMvc.perform(as(patch("/api/admin/users/" + id), adminBearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void tokenCarregaRoleETv() throws Exception {
        bearer("tk_claims", Role.OPERADOR);
        JsonNode c = claims(login("tk_claims").get("accessToken").asText());
        assertThat(c.get("role").asText()).isEqualTo("OPERADOR");
        assertThat(c.get("tv").asInt()).isZero();
        assertThat(c.get("sub").asText()).isEqualTo("tk_claims");
    }

    @Test
    void userPromovidoAOperador_tokenAntigoRecusado_tokenNovoAcessaAlertas() throws Exception {
        bearer("tk_promovido", Role.USER);
        String old = "Bearer " + login("tk_promovido").get("accessToken").asText();
        mockMvc.perform(as(get("/api/alerts"), old)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/auth/me"), old)).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("USER")).andExpect(jsonPath("$.username").value("tk_promovido"))
                .andExpect(jsonPath("$.id").isNumber()).andExpect(jsonPath("$.email").isEmpty()).andExpect(jsonPath("$.name").isEmpty());

        patchUser(adminToken(), id("tk_promovido"), "{\"role\":\"OPERADOR\"}");

        mockMvc.perform(as(get("/api/alerts"), old)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"TOKEN_REVOKED\"}"));
        assertThat(jdbc.queryForObject("SELECT token_version FROM users WHERE username = 'tk_promovido'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_token WHERE user_id = ?", Integer.class, id("tk_promovido")))
                .as("refresh tokens revogados").isZero();

        String novo = "Bearer " + login("tk_promovido").get("accessToken").asText();
        mockMvc.perform(as(get("/api/alerts"), novo)).andExpect(status().isOk());
        mockMvc.perform(as(get("/api/auth/me"), novo)).andExpect(jsonPath("$.role").value("OPERADOR"));
    }

    @Test
    void adminRebaixadoPerdeApiAdminNaHora() throws Exception {
        bearer("tk_admin2", Role.ADMIN);
        String old = "Bearer " + login("tk_admin2").get("accessToken").asText();
        mockMvc.perform(as(get("/api/admin/users"), old)).andExpect(status().isOk());

        patchUser(adminToken(), id("tk_admin2"), "{\"role\":\"USER\"}");

        mockMvc.perform(as(get("/api/admin/users"), old)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"TOKEN_REVOKED\"}"));
        String novo = "Bearer " + login("tk_admin2").get("accessToken").asText();
        mockMvc.perform(as(get("/api/admin/users"), novo)).andExpect(status().isForbidden());
    }

    @Test
    void desativado_401_eReativadoComTokenNovo() throws Exception {
        bearer("tk_inativo", Role.USER);
        String old = "Bearer " + login("tk_inativo").get("accessToken").asText();
        patchUser(adminToken(), id("tk_inativo"), "{\"active\":false}");
        mockMvc.perform(as(get("/api/auth/me"), old)).andExpect(status().isUnauthorized());
        patchUser(adminToken(), id("tk_inativo"), "{\"active\":true}");
        mockMvc.perform(as(get("/api/auth/me"), old)).andExpect(status().isUnauthorized()); // tv mudou duas vezes
        mockMvc.perform(as(get("/api/auth/me"), "Bearer " + login("tk_inativo").get("accessToken").asText())).andExpect(status().isOk());
    }

    @Test
    void editarEmailOuNomeNaoRevogaOToken() throws Exception {
        bearer("tk_nome", Role.USER);
        String t = "Bearer " + login("tk_nome").get("accessToken").asText();
        patchUser(adminToken(), id("tk_nome"), "{\"name\":\"Nome Novo\",\"email\":\"nome.novo@exemplo.com\"}");
        mockMvc.perform(as(get("/api/auth/me"), t)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nome Novo")).andExpect(jsonPath("$.email").value("nome.novo@exemplo.com"));
    }

    @Test
    void tokenSemRoleOuTv_eTokenDeUsuarioInexistente_401() throws Exception {
        bearer("tk_legado", Role.USER);
        var key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        String legado = Jwts.builder().subject("tk_legado").issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        mockMvc.perform(as(get("/api/auth/me"), "Bearer " + legado)).andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"code\":\"TOKEN_REVOKED\"}"));
        String fantasma = Jwts.builder().subject("nao_existe_mesmo").claim("role", "ADMIN").claim("tv", 0).issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000)).signWith(key).compact();
        mockMvc.perform(as(get("/api/admin/users"), "Bearer " + fantasma)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokenExpiradoOuLixoContinuaAnonimo403() throws Exception {
        var key = Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        String expirado = Jwts.builder().subject("it_admin").claim("role", "ADMIN").claim("tv", 0)
                .expiration(new Date(System.currentTimeMillis() - 60_000)).signWith(key).compact();
        mockMvc.perform(as(get("/api/admin/users"), "Bearer " + expirado)).andExpect(status().isForbidden());
        mockMvc.perform(as(get("/api/admin/users"), "Bearer lixo")).andExpect(status().isForbidden());
    }

    @Test
    void refreshEmiteORoleAtualDoBanco() throws Exception {
        bearer("tk_refresh", Role.USER);
        JsonNode l = login("tk_refresh");
        // muda o perfil direto no banco (sem passar pela API, que revogaria o refresh token)
        jdbc.update("UPDATE users SET role = 'OPERADOR' WHERE username = 'tk_refresh'");
        String r = mockMvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + l.get("refreshToken").asText() + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(claims(objectMapper.readTree(r).get("accessToken").asText()).get("role").asText()).isEqualTo("OPERADOR");
    }

    @Test
    void cookieHttpOnlyDoAccessTokenAutenticaOStream() throws Exception {
        bearer("tk_cookie", Role.OPERADOR);
        String jwt = login("tk_cookie").get("accessToken").asText();
        mockMvc.perform(get("/api/stream").accept("text/event-stream").cookie(new Cookie("accessToken", jwt)))
                .andExpect(request().asyncStarted());
        mockMvc.perform(get("/api/stream").accept("text/event-stream")).andExpect(status().isForbidden());
    }
}
