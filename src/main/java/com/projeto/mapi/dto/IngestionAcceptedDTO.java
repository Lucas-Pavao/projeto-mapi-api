package com.projeto.mapi.dto;

// Resposta estruturada para os endpoints de ingestão administrativa (antes cada um devolvia uma
// String solta escrita à mão — sem estrutura, sem padrão com o resto da API).
public record IngestionAcceptedDTO(String message) {
}
