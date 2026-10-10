package com.projeto.mapi.service.flood;

import com.projeto.mapi.dto.FloodPredictionResponseDTO;

/**
 * Resultado de uma chamada à IA: o payload do contrato (que leva ao cliente só a parte semântica da
 * versão do modelo) e a versão COMPLETA, com o hash git, que fica só no log e na auditoria
 * (ADR 0007 §5).
 */
public record PredictionResult(FloodPredictionResponseDTO response, String auditModelVersion) {
}
