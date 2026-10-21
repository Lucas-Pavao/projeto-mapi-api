package com.projeto.mapi.security;

import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * Autentica pelo access token (header Bearer ou cookie httpOnly "accessToken"). O perfil vem do claim {@code role},
 * não do banco (ADR 0010, emenda 2). Token válido de usuário inativo, inexistente, sem {@code role}/{@code tv} ou com
 * {@code tv} diferente de {@code users.token_version} recebe 401 {@code {"code":"TOKEN_REVOKED"}}.
 * Token ausente, malformado ou expirado segue anônimo (o resto da cadeia responde 403, como sempre).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String REVOKED_BODY = "{\"code\":\"TOKEN_REVOKED\"}";

    private final JwtService jwtService;
    private final UserAccessCache accessCache;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String jwt = extractToken(request);
        if (jwt == null) {
            filterChain.doFilter(request, response);
            return;
        }
        Optional<JwtService.TokenData> data = jwtService.parse(jwt);
        if (data.isEmpty()) { // inválido ou expirado: anônimo
            filterChain.doFilter(request, response);
            return;
        }
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            Optional<User> current = accessCache.find(data.get().username());
            Role role = parseRole(data.get().role());
            if (current.isEmpty() || !current.get().isActive() || role == null || data.get().tokenVersion() == null
                    || data.get().tokenVersion() != current.get().getTokenVersion()) {
                revoked(response);
                return;
            }
            // Principal com o perfil DO TOKEN (as authorities nascem do claim, não do banco).
            User u = current.get();
            User principal = User.builder().id(u.getId()).username(u.getUsername()).password(u.getPassword())
                    .role(role).active(true).email(u.getEmail()).name(u.getName()).tokenVersion(u.getTokenVersion())
                    .lastLoginAt(u.getLastLoginAt()).build();
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    principal, null, List.of(new SimpleGrantedAuthority(role.name())));
            auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    private static void revoked(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(REVOKED_BODY);
    }

    private static Role parseRole(String raw) {
        try {
            return raw == null ? null : Role.valueOf(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Prioriza o header Authorization (clientes de API/scripts), com fallback pro cookie httpOnly "accessToken"
    // (navegador via SPA, inclusive o stream SSE com credentials).
    private String extractToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) return authHeader.substring(7);
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if ("accessToken".equals(cookie.getName())) return cookie.getValue();
        }
        return null;
    }
}
