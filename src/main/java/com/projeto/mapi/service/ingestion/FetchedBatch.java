package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Um lote baixado da fonte: registros, estações e a descrição da requisição (URL, SHA-256 da resposta).
 *
 * @param inTransaction ação opcional executada na MESMA transação do lote, depois do bruto e da observação gravados
 *                      (ex.: marcar a janela como carregada, para a carga retomável do Cemaden PED). Nula = nenhuma.
 */
public record FetchedBatch(List<RawRecord> records, List<StationInfo> stations, JsonNode requestInfo,
                           Runnable inTransaction) {

    public FetchedBatch(List<RawRecord> records, List<StationInfo> stations, JsonNode requestInfo) {
        this(records, stations, requestInfo, null);
    }
}
