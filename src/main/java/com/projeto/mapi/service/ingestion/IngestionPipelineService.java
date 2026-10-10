package com.projeto.mapi.service.ingestion;

import java.util.Optional;
import java.util.UUID;

/** Orquestra staging bruto, validação com quarentena e consolidação versionada (ADR 0006). */
public interface IngestionPipelineService {

    /** Executa de forma síncrona e devolve o ingestion_run fechado. */
    IngestionRunView execute(IngestionRequest request);

    /** Abre o ingestion_run, agenda a execução no taskExecutor e devolve o id (resposta 202 do endpoint). */
    UUID submit(IngestionRequest request);

    Optional<IngestionRunView> findRun(UUID id);
}
