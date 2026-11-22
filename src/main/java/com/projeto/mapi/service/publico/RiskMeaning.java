package com.projeto.mapi.service.publico;

/**
 * Manchetes de "Minha rua" pelo nível do alerta por regressão (prompt de 20/11, seção 5.10). Mesmos textos de
 * projeto-mapi-front/src/features/public-risk/components/PublicRiskResult.tsx; mudança lá precisa ser repetida aqui.
 * Nunca "risco baixo" nem "seguro".
 */
public final class RiskMeaning {

    public static final String NO_POINT = "Não há ponto monitorado perto deste endereço.";
    public static final String IN_TEST = "O alerta de alagamento ainda está em teste. Fique de olho na chuva e siga as orientações da Defesa Civil.";

    private RiskMeaning() {
    }

    /** @param level ALERTA, ATENCAO, SEM_ALERTA ou SEM_DADO */
    public static String of(String level) {
        return switch (level) {
            case "ALERTA" -> "Alerta de alagamento perto de você na próxima hora.";
            case "ATENCAO" -> "Atenção: pode alagar perto de você se a chuva for forte.";
            case "SEM_ALERTA" -> "Sem alerta de alagamento nas próximas 6 horas. Fique de olho na chuva.";
            default -> "Sem dado suficiente agora para dizer se vai alagar. Isto não quer dizer que está seguro.";
        };
    }
}
