package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.dto.LabelInventoryDTO;

/** Deduplicação de rótulos entre fontes e inventário (ADR 0019). */
public interface OccurrenceClusterService {

    /** Recalcula os grupos (derivados) e devolve o inventário; {@code pointRadiusM} escolhe o linkage do casamento. */
    LabelInventoryDTO rebuild(double pointRadiusM);
}
