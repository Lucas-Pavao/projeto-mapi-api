package com.projeto.mapi.service.ingestion.occurrence;

import com.projeto.mapi.service.ingestion.IngestionRunView;

import java.util.List;

/** Pipeline de rótulos CKAN (ADR 0006 seção 6): bruto -> flood_occurrence -> occurrence_point_match. */
public interface OccurrenceIngestionService {

    String SOURCE = "CKAN_RECIFE";

    /** Grava o lote como um ingestion_run, classifica, geocodifica e casa com os pontos pelo raio vigente. */
    IngestionRunView ingest(String packageId, String resourceId, List<CkanRawRecord> records);

    /**
     * REVALIDATE: reprocessa o bruto já gravado de um recurso (sem ir ao portal) com as regras e o mapeamento de
     * colunas vigentes (F2-BACK-18). Ocorrências novas são gravadas e geocodificadas; quarentenas {@code TS} que
     * deixaram de se aplicar são fechadas (nunca apagadas). Idempotente.
     */
    IngestionRunView revalidate(String packageId, String resourceId);

    /** Resumo do preenchimento de {@code geocode_level}/{@code neighborhood} das ocorrências legadas. */
    record GeocodeLevelBackfill(int examined, int none, int street, int neighborhood, int city, int unverifiable,
                                int neighborhoodFilled) {}

    /**
     * Preenche {@code neighborhood} (do bruto) e {@code geocode_level} (F2-BACK-20) das ocorrências com nível nulo:
     * NONE se não há coordenada; senão o nível só se o geocodificador, consultado de novo, devolve a MESMA
     * coordenada gravada (verificável). Sem verificação o nível fica NULL: não se inventa.
     */
    GeocodeLevelBackfill backfillGeocodeLevels();

    /**
     * Refaz o casamento de todas as ocorrências geocodificadas com um raio. Cria (ou reutiliza) o
     * {@code linkage_config} do raio; linhas de outros raios permanecem (análise de sensibilidade).
     * Devolve o id do linkage_config usado.
     */
    long rematch(double radiusM);
}
