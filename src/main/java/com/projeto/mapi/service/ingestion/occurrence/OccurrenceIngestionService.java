package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.service.ingestion.IngestionRunView;

import java.util.List;

/** Pipeline de rótulos CKAN (ADR 0006 seção 6): bruto -> flood_occurrence -> occurrence_point_match. */
public interface OccurrenceIngestionService {

    String SOURCE = "CKAN_RECIFE";

    /** Grava o lote como um ingestion_run, classifica, geocodifica e casa com os pontos pelo raio vigente. */
    IngestionRunView ingest(String packageId, String resourceId, List<CkanRawRecord> records);

    /**
     * Refaz o casamento de todas as ocorrências geocodificadas com um raio. Cria (ou reutiliza) o
     * {@code linkage_config} do raio; linhas de outros raios permanecem (análise de sensibilidade).
     * Devolve o id do linkage_config usado.
     */
    long rematch(double radiusM);
}
