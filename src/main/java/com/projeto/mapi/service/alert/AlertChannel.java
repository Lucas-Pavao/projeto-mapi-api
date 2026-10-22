package com.projeto.mapi.service.alert;

import com.projeto.mapi.dto.AlertDTO;

/**
 * Canal de entrega de alerta (ADR 0011). BROWSER é atendido pelo stream SSE (ADR 0012); e-mail e WhatsApp
 * entram só como esta interface, sem implementação.
 */
public interface AlertChannel {
    String name();

    void deliver(AlertDTO alert);
}
