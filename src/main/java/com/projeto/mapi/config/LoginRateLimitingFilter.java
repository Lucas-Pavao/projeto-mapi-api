package com.projeto.mapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limitador de taxa simples (janela deslizante, em memória) para /api/auth/login e
 * /api/auth/register — os dois únicos endpoints públicos e não autenticados que aceitam
 * credenciais, e portanto os alvos naturais de força bruta / spam de contas.
 *
 * Limitação conhecida: o estado é em memória local, então só protege uma instância. Se a API
 * rodar com múltiplas réplicas atrás de um load balancer, isso precisa virar um limitador
 * compartilhado (ex: Redis) — não é o caso hoje (docker-compose sobe uma única instância).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
@Slf4j
public class LoginRateLimitingFilter extends OncePerRequestFilter {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final Set<String> PROTECTED_PATHS = Set.of("/api/auth/login", "/api/auth/register");

    private final ConcurrentHashMap<String, Deque<Instant>> attemptsByKey = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isProtected(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = clientKey(request);
        Deque<Instant> timestamps = attemptsByKey.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
        Instant now = Instant.now();

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && Duration.between(timestamps.peekFirst(), now).compareTo(WINDOW) > 0) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_ATTEMPTS) {
                log.warn("Rate limit excedido em {} para {}", request.getRequestURI(), key);
                response.setStatus(429);
                response.setHeader("Retry-After", String.valueOf(WINDOW.toSeconds()));
                response.setContentType("application/json");
                response.getWriter().write(
                        "{\"statusCode\":429,\"message\":\"Muitas tentativas. Tente novamente em instantes.\"}");
                return;
            }
            timestamps.addLast(now);
        }

        filterChain.doFilter(request, response);
    }

    // Evita que o mapa cresça indefinidamente com chaves de IPs que não voltam a tentar.
    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void cleanupStaleEntries() {
        Instant cutoff = Instant.now().minus(WINDOW);
        attemptsByKey.entrySet().removeIf(entry -> {
            Deque<Instant> timestamps = entry.getValue();
            synchronized (timestamps) {
                return timestamps.isEmpty() || timestamps.peekLast().isBefore(cutoff);
            }
        });
    }

    private boolean isProtected(HttpServletRequest request) {
        return "POST".equalsIgnoreCase(request.getMethod()) && PROTECTED_PATHS.contains(request.getRequestURI());
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        String ip = (forwardedFor != null && !forwardedFor.isBlank())
                ? forwardedFor.split(",")[0].trim()
                : request.getRemoteAddr();
        return ip + ":" + request.getRequestURI();
    }
}
