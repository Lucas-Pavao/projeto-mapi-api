package com.projeto.mapi.service.ingestion;

import java.util.Optional;
import java.util.UUID;

/** Orquestra staging bruto, validação com quarentena e consolidação versionada (ADR 0006). */
public interface IngestionPipelineService {

    /** Executa de forma síncrona e devolve o ingestion_run fechado. */
    IngestionRunView execute(IngestionRequest request);

    /** Abre o ingestion_run, agenda a execução no taskExecutor e devolve o id (resposta 202 do endpoint). */
    UUID submit(IngestionRequest request);

    /**
     * Fontes ao vivo sem API de reconsulta (ANA, APAC meteorologia24h, Cemaden): o coletor entrega o lote já
     * baixado e o pipeline faz staging, validação e consolidação como em {@link #execute}. Cria um
     * ingestion_run LIVE sem intervalo. Falha do lote vira run FAILED; fonte sem adaptador lança InvalidRequestException.
     */
    IngestionRunView executePushed(String source, FetchedBatch batch);

    Optional<IngestionRunView> findRun(UUID id);
}
