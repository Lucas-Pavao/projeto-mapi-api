package com.projeto.mapi.dto;

import java.time.Instant;
import java.util.List;

/**
 * Leitura por horizonte de um ponto (prompt dos horizontes, bloco D): três camadas lado a lado, SEM combinar.
 * <ul>
 *   <li>{@code official}: o que o INMET disse (avisos e previsão por turno), com o instante em que o MAPi viu;</li>
 *   <li>{@code dayRisk}: o "dia de risco" do MAPi. Ausente com motivo {@code NAO_VALIDADO} enquanto não passar no
 *       critério do bloco C (reports/day_risk/RELATORIO.md no projeto-mapi-ai);</li>
 *   <li>{@code nextHour}: o alerta por ponto do MAPi (alerta de alagamento por regressão), como já existe.</li>
 * </ul>
 * Cada camada traz origem, idade e motivo quando falta. Nenhum número do INMET é reescrito nem misturado com os do MAPi.
 */
public record HorizonsDTO(
        String slug,
        String name,
        String municipality,
        String ibge,
        Instant generatedAt,
        Official official,
        DayRisk dayRisk,
        NextHour nextHour
) {

    /** INMET. {@code warningsReason}: a coleta de avisos está parada (lista vazia não quer dizer "sem aviso"). */
    public record Official(String origin, String attribution, List<OfficialWarningDTO> warnings, String warningsReason,
                           String warningsReasonText, Instant warningsCheckedAt, OfficialForecastDTO forecast) {}

    public record DayRisk(boolean available, String origin, String version, String reason, String reasonText, String report) {}

    /**
     * Alerta por ponto. {@code inTest}: modo sombra e o usuário não é administrador; o nível vai nulo e a tela diz
     * "em teste". Sem estado do ponto, {@code level} = SEM_DADO com motivo (nunca "sem alerta").
     */
    public record NextHour(String origin, String level, Integer worstHorizonHours, boolean experimental, boolean inTest,
                           Instant issuedAt, Long ageMinutes, String reason, String reasonText) {}
}
