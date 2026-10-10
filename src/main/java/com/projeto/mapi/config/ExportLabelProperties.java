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
     * horizonte de cada coluna. Origem: MODELAGEM-IA A.3.1 item 6 (proposta "G = H", [sem fonte]).
     * [decisão do autor pendente — pergunta 25]
     */
    private Integer postEventWindowHours;

    /**
     * Id do linkage_config (kind OCCURRENCE_POINT) usado no rótulo. Vazio = o config cujo raio é
     * {@code mapi.ingestion.occurrence-radius-m}. O raio final é decisão do autor (MODELAGEM-IA A.3.1 item 4
     * propõe 500 m, [sem fonte]). [decisão do autor pendente — pergunta 25]
     */
    private Long linkageConfigId;

    public int postEventHours(int horizonHours) {
        return postEventWindowHours != null ? postEventWindowHours : horizonHours;
    }
}
