package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Alerta de chuva por ponto (ADR 0026). Cada valor tem a origem anotada; nenhum foi escolhido olhando o teste.
 * Os de histerese, duração mínima e agrupamento vêm da validação da simulação do dado como chega (Etapa 3,
 * projeto-mapi-ai/reports/rain_v1b/), e até lá ficam neutros (desliga = liga, duração mínima 0).
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.alerta")
public class AlertaProperties {

    private boolean enabled = true;
    /** Modo sombra: grava e confere, não aparece como oficial. Só o Lucas desliga (ADR 0026). */
    private boolean shadow = true;
    /** Frescor do alerta (item 2.2): leitura mais nova do ponto com no máximo 15 min. Os 70 min servem à série. */
    private int freshnessMinutes = 15;

    private Rain rain = new Rain();
    private Capacity capacity = new Capacity();
    private Flood flood = new Flood();
    private Latency latency = new Latency();

    @Data
    public static class Rain {
        /** L_liga. Nulo = o limiar de maior CSI que a IA devolve (models/v4/rain/metadata.json, 1 h: 0,1055). */
        private Double probOn;
        /** L_desliga. Nulo = igual a L_liga (sem histerese) até a Etapa 3 escolher. */
        private Double probOff;
        private int minDurationMinutes = 0;
        /** Sem renovação (p >= L_liga ou chuva acima da galeria) por 60 min, volta a NENHUM (prompt, item 3.1). */
        private int expiryMinutes = 60;
        /** Cancelamento: chuva abaixo de 0,2 mm nos últimos 30 min (prompt, item 3.1). */
        private int cancelDryMinutes = 30;
        private double dryMm = 0.2;
        /** Agrupamento: disparos separados por menos de 60 min de chuva abaixo de 0,2 mm são o mesmo evento. */
        private int eventGapMinutes = 60;
    }

    @Data
    public static class Capacity {
        /** TR de projeto da microdrenagem (PARAMETROS-MODELO-FISICO.md, item 7: central 5 anos). */
        private double trYears = 5.0;
        /** Tempo de entrada Te (item 10: central 10 min). */
        private double teMinutes = 10.0;
    }

    @Data
    public static class Flood {
        /** h_crit (item 9: 15 cm). */
        private double hcritMm = 150.0;
        /** PROVAVEL se a projeção com o quantil de 50% chega a h_crit em até 60 min (fixado antes da reprise). */
        private int horizonMinutes = 60;
        private String projectionQuantile = "q50";
        /** O balde roda sobre as últimas 24 h da série de 10 min, a partir da lacuna mais recente. */
        private int seriesHours = 24;
        /** Precisa de pelo menos 6 h contínuas sem lacuna antes de agora; senão SEM_DADO (SERIE_INCOMPLETA). */
        private int minCompleteHours = 6;
        /** Pontos com terreno em revisão: SEM_DADO, nunca NAO. Derby: área de 62 m², regra da área pendente. */
        private List<String> terrainReview = List.of("AGAMENON_DERBY");
        private double coefficientC = 0.86;
    }

    @Data
    public static class Latency {
        /** Orçamento (item 2.1): da estação ao envio pelo SSE, p90 abaixo de 5 min em hora com chuva. */
        private int budgetP90Minutes = 5;
        /** Hora com chuva: 0,2 mm ou mais. */
        private double wetHourMm = 0.2;
    }
}
