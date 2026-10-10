package com.projeto.mapi.service.ingestion;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/** Um lote baixado da fonte: registros, estações e a descrição da requisição (URL, SHA-256 da resposta). */
public record FetchedBatch(List<RawRecord> records, List<StationInfo> stations, JsonNode requestInfo) {}
