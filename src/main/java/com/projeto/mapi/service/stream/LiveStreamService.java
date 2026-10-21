package com.projeto.mapi.service.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Stream de eventos ao vivo (ADR 0012). */
public interface LiveStreamService {

    /**
     * Abre um stream para o usuário. {@code TooManyRequestsException} (429) se ele já tem 3 abertos.
     * Eventos {@code alert} só chegam a quem {@code receivesAlerts} (OPERADOR e ADMIN, ADR 0010).
     * {@code lastEventId} reenvia o que ainda está no buffer recente.
     */
    SseEmitter subscribe(String username, boolean receivesAlerts, String lastEventId);

    int activeConnections();

    /** Encerra os streams abertos do usuário (perfil ou ativação mudou; o cliente reconecta com o token novo). */
    void disconnect(String username);
}
