package com.projeto.mapi.dto;

import java.util.List;

/** Resposta JSON da tabela de eventos: manifesto mais linhas. */
public record RainEventTableDTO(RainEventManifestDTO manifest, List<RainEventDTO> events) {
}
