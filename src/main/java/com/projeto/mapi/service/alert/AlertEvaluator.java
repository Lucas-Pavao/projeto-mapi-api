package com.projeto.mapi.service.alert;

/**
 * Avalia as regras ativas contra o estado atual e grava alertas só na transição (entrada em condição),
 * com supressão de 30 min por regra e alvo (ADR 0011).
 */
public interface AlertEvaluator {

    /** Devolve quantos alertas novos foram gravados. */
    int evaluate();
}
