package com.projeto.mapi.service.ingestion.adapter;

import com.projeto.mapi.service.ingestion.DraftValue;
import com.projeto.mapi.service.ingestion.FetchedBatch;
import com.projeto.mapi.service.ingestion.RawRecord;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Adaptador de uma fonte: baixa e entrega registros de origem (declarando o fuso) e extrai os valores de
 * um registro bruto. NÃO converte fuso e NÃO valida: isso é da validação (ADR 0006 seção 4).
 */
public interface SourceAdapter {

    /** Nome da fonte (coluna source). */
    String source();

    /** Fuso declarado pelo parser (IANA, UTC ou UNVERIFIED); fica no bruto para auditoria. */
    String declaredTz();

    /** Versão do parser, gravada em ingestion_run.parser_version. */
    String parserVersion();

    /**
     * Baixa os registros de origem de {@code [from, to)} em lotes, chamando {@code sink} para cada um.
     * Falha da agência vira {@link com.projeto.mapi.exception.SourceUnavailableException}.
     */
    void fetch(Instant from, Instant to, Consumer<FetchedBatch> sink);

    /** Extrai os valores do payload de um registro bruto (usado também no REVALIDATE). */
    List<DraftValue> extract(RawRecord record);
}
