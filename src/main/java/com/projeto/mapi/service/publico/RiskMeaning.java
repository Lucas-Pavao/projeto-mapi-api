package com.projeto.mapi.service.publico;

import com.projeto.mapi.dto.RiskLevel;

/**
 * Textos de RISK_MEANING, copiados sem reescrita de projeto-mapi-front/src/components/risk/risk.ts (branch
 * feat/redesign-f1, linhas 73-78). Qualquer mudança lá precisa ser repetida aqui.
 */
public final class RiskMeaning {

    public static final String NO_POINT = "Não há ponto monitorado perto deste endereço.";

    private RiskMeaning() {
    }

    public static String of(RiskLevel level) {
        return switch (level) {
            case LOW -> "Probabilidade de alagamento baixa na estimativa do modelo. Segue valendo acompanhar sensores e chuva.";
            case MEDIUM -> "Probabilidade intermediária. Vale acompanhar o ponto e conferir os sensores próximos.";
            case HIGH -> "Probabilidade alta. Priorize a verificação do ponto; a classe não confirma alagamento no local.";
            case UNKNOWN -> "O modelo não respondeu ou faltam dados. Isto não significa risco baixo: use sensores e observação em campo.";
        };
    }
}
