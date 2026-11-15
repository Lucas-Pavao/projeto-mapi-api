package com.projeto.mapi.dto;

import java.util.List;

/** Resposta JSON do resumo por tipo: manifesto mais linhas. */
public record RainEventSummaryTableDTO(RainEventManifestDTO manifest, List<RainEventSummaryDTO> rows) {
}
