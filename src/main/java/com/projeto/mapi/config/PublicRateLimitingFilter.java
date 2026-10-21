package com.projeto.mapi.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Limite de taxa de {@code /api/public/**} (ADR 0013): 30 requisições por minuto por IP, janela deslizante em
 * memória, no mesmo padrão do {@link LoginRateLimitingFilter}. Acima disso, 429 com Retry-After. Não registra a
 * coordenada nem a query string. Mesma limitação do filtro de login: estado local de uma única instância, e o IP
 * vem do primeiro valor de X-Forwarded-For quando existe (o nginx do front o define; atrás de outro proxy, ajustar).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@Slf4j
public class PublicRateLimitingFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);

    private final int maxPerWindow;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> hitsByIp = new ConcurrentHashMap<>();

    public PublicRateLimitingFilter(@Value("${mapi.public.rate-limit-per-minute:30}") int maxPerWindow, Clock clock) {
        this.maxPerWindow = maxPerWindow;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/public/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String ip = clientIp(request);
        Deque<Instant> hits = hitsByIp.computeIfAbsent(ip, k -> new ConcurrentLinkedDeque<>());
        Instant now = clock.instant();
        long retryAfter = 0;
        synchronized (hits) {
            while (!hits.isEmpty() && Duration.between(hits.peekFirst(), now).compareTo(WINDOW) >= 0) hits.pollFirst();
            if (hits.size() >= maxPerWindow) {
                retryAfter = Math.max(1, WINDOW.minus(Duration.between(hits.peekFirst(), now)).toSeconds() + 1);
            } else {
                hits.addLast(now);
            }
        }
        if (retryAfter > 0) {
            log.warn("Limite de taxa excedido em /api/public/** (IP {})", ip);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfter));
            response.setContentType("application/json");
            response.getWriter().write("{\"statusCode\":429,\"message\":\"Muitas consultas. Tente novamente em instantes.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    @Scheduled(fixedRate = 10, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void cleanupStaleEntries() {
        Instant cutoff = clock.instant().minus(WINDOW);
        hitsByIp.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                return e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff);
            }
        });
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank()) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
    }
}
