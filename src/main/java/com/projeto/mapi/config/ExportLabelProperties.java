package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Rótulo do export de treino (MODELAGEM-IA A.1.4 e A.3.1, ADR 0006 seção 6, ADR 0025 proposta).
 * Prefixo {@code mapi.export.label}. O rótulo vem de flood_occurrence + occurrence_point_match; nunca de flood_events.
 * Por padrão {@code stratified.enabled = false} para manter o export atual (500 m, pergunta 25) até aprovação na validação.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "mapi.export.label")
public class ExportLabelProperties {

    /** Horizontes H (horas) das colunas label_h&lt;H&gt;. Origem: MODELAGEM-IA A.1.4 (H = 6 h principal, 3 h secundário). */
    private List<Integer> horizonsHours = List.of(6, 3);

    /**
     * Janela pós-evento G (horas): instâncias com ocorrência em (t - G, t] ficam excluídas. Vazio = G igual ao
     * horizonte de cada coluna. Origem: MODELAGEM-IA A.3.1 item 6 (G = H); aprovado pelo autor,
     * pergunta 25, 2026-10-10 (G = 6 h para label_h6, 3 h para label_h3).
     */
    private Integer postEventWindowHours;

    /**
     * Id do linkage_config (kind OCCURRENCE_POINT) usado no rótulo. Vazio = o config de menor id cujo raio é
     * {@link #linkageRadiusM}. Não apaga nem altera configs antigos (análise de sensibilidade troca o id).
     */
    private Long linkageConfigId;

    /**
     * Raio (m) do casamento ocorrência-ponto usado quando {@link #linkageConfigId} está vazio. Padrão 500 m:
     * decisão do autor, pergunta 25, 2026-10-10 (proposta em MODELAGEM-IA A.3.1 item 4). Configurável para
     * sensibilidade {250, 500, 1000, 3000}.
     */
    private double linkageRadiusM = 500.0;

    /** Modo estrito padrão (ADR 0025): false mantém Prata (STREET <= 400 m) disponível para treino ponderado. */
    private boolean strictOnlyDefault = false;

    /** Raio Ouro (m): ADDRESS / SOURCE_COORDINATE / FloodReport validado. Proposta do Lucas (ADR 0025); verificar na validação. */
    private double radiusPointM = 250.0;

    /** Raio Prata (m): STREET (logradouro sem número). Proposta do Lucas (ADR 0025); verificar na validação. */
    private double radiusStreetM = 400.0;

    /** Raio Bronze (m): NEIGHBORHOOD quando cluster >= minClusterSizeNeighborhood. Proposta do Lucas (ADR 0025). */
    private double radiusClusterM = 600.0;

    /** Tamanho mínimo do cluster temporal para estrato Bronze em NEIGHBORHOOD. */
    private int minClusterSizeNeighborhood = 3;

    /** Janela temporal antes do pico de chuva do RainEventDetector (horas). */
    private int windowBeforeHours = 2;

    /** Janela temporal depois do pico de chuva do RainEventDetector (horas). */
    private int windowAfterHours = 6;

    private Weights weights = new Weights();
    private Stratified stratified = new Stratified();

    @Data
    public static class Weights {
        private double high = 1.0;
        private double medium = 0.7;
        private double low = 0.4;
        private double reanalysisPenalty = 0.6;
        private double distantStationPenalty = 0.85;
    }

    @Data
    public static class Stratified {
        /** Desligado por padrão para preservar o export vigente (500 m) enquanto os estratos atuais estiverem vazios. */
        private boolean enabled = false;
    }

    public int postEventHours(int horizonHours) {
        return postEventWindowHours != null ? postEventWindowHours : horizonHours;
    }
}
