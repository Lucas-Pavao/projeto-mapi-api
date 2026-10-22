package com.projeto.mapi.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

import java.time.Instant;

/**
 * Resultado congelado da avaliação. {@code result} é o texto literal do arquivo, inserido na resposta sem
 * reserialização (nenhum número é reformatado nem recalculado).
 */
public record ModelEvaluationDTO(String sha256, String source, Instant frozenAt, @JsonRawValue String result) {
}
