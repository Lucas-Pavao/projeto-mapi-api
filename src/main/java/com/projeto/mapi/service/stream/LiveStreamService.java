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

    /** Como o anterior; {@code admin} recebe também os eventos do alerta em modo sombra (ADR 0026). */
    default SseEmitter subscribe(String username, boolean receivesAlerts, boolean admin, String lastEventId) {
        return subscribe(username, receivesAlerts, lastEventId);
    }

    /**
     * Publica um evento do alerta por ponto. {@code shadow} = só administradores recebem (modo sombra). Devolve o
     * instante do envio (item 2.1).
     */
    default java.time.Instant publishAlertEvent(String name, Object data, boolean shadow) {
        return null;
    }

    int activeConnections();

    /** Encerra os streams abertos do usuário (perfil ou ativação mudou; o cliente reconecta com o token novo). */
    void disconnect(String username);
}
