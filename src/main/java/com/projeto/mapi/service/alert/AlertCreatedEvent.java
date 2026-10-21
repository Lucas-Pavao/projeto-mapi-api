package com.projeto.mapi.service.alert;

import com.projeto.mapi.dto.AlertDTO;

/** Publicado quando o avaliador grava um alerta novo; o stream SSE o repassa a OPERADOR e ADMIN. */
public record AlertCreatedEvent(AlertDTO alert) {
}
