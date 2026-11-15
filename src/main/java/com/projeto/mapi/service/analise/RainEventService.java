package com.projeto.mapi.service.analise;

import com.projeto.mapi.dto.RainEventSummaryTableDTO;
import com.projeto.mapi.dto.RainEventTableDTO;

/** Caso de uso: tabela de eventos de chuva e resumo por tipo (ADR 0017). Só leitura. */
public interface RainEventService {

    /** Tabela + CSV; o {@code datasetId} do manifesto é o SHA-256 do CSV. */
    record EventsResult(RainEventTableDTO table, String csv) {}

    record SummaryResult(RainEventSummaryTableDTO table, String csv) {}

    EventsResult events(RainEventParams params);

    SummaryResult summary(RainEventParams params);
}
