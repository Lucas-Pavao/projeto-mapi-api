package com.projeto.mapi.service.alagamento;

import com.fasterxml.jackson.databind.JsonNode;

/** GET /health da MAPI AI. Nunca lança: sem resposta devolve {@code null}. */
public interface AiHealthClient {
    JsonNode health();
}
