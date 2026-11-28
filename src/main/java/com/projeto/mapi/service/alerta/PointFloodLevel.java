package com.projeto.mapi.service.alerta;

/**
 * Campo {@code point_flood}: lâmina do balde a 10 min no terreno do ponto (ALAGAMENTO_PROVAVEL quando PROVAVEL).
 * DESLIGADO (ADR 0030, V47): o ciclo do ADR 0026 não calcula mais lâmina; o nível de alagamento é o do alerta único.
 */
public enum PointFloodLevel {
    NAO, PROVAVEL, SEM_DADO, DESLIGADO
}
