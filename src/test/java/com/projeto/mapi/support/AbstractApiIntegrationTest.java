package com.projeto.mapi.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import com.projeto.mapi.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Base dos testes de API com TimescaleDB real (ADR 0004) e MockMvc passando pela cadeia de segurança
 * completa. Dá usuários e tokens por perfil (ADR 0010) para a matriz de autorização.
 */
@AutoConfigureMockMvc
public abstract class AbstractApiIntegrationTest extends AbstractTimescaleIntegrationTest {

    public static final String SENHA = "SenhaDeTeste#123";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected PasswordEncoder passwordEncoder;
    @Autowired protected JwtService jwtService;

    /** Garante o usuário (idempotente) e devolve o cabeçalho Authorization. */
    protected String bearer(String username, Role role) {
        User user = userRepository.findByUsername(username).orElseGet(() -> userRepository.save(User.builder()
                .username(username)
                .password(passwordEncoder.encode(SENHA))
                .role(role)
                .build()));
        return "Bearer " + jwtService.generateToken(user.getUsername());
    }

    protected String userToken() { return bearer("it_user", Role.USER); }
    protected String operadorToken() { return bearer("it_operador", Role.OPERADOR); }
    protected String adminToken() { return bearer("it_admin", Role.ADMIN); }

    protected MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder req, String bearer) {
        return bearer == null ? req : req.header(HttpHeaders.AUTHORIZATION, bearer);
    }
}
