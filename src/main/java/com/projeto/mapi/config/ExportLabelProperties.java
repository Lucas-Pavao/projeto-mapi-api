package com.projeto.mapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Rótulo do export de treino (MODELAGEM-IA A.1.4 e A.3.1, ADR 0006 seção 6). Prefixo {@code mapi.export.label}.
 * O rótulo vem de flood_occurrence + occurrence_point_match; nunca de flood_events.
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

    public int postEventHours(int horizonHours) {
        return postEventWindowHours != null ? postEventWindowHours : horizonHours;
    }
}
