package com.projeto.mapi.service.ingestion.freshness;

import com.projeto.mapi.dto.IngestionSourceStatusDTO.Freshness;

import java.util.Map;

/** Fonte velha (plano de melhorias de 2026-11-16, item 1.4): idade da leitura aceita contra o limite da fonte. */
public interface SourceFreshnessService {

    /** Estado de frescor de cada fonte do source_registry. */
    Map<String, Freshness> freshness();

    /** Abre alerta SOURCE_STALE para cada fonte parada (um por episódio, lembrete a cada 24 h). Devolve quantos abriu. */
    int raiseAlerts();
}
