package com.projeto.mapi.service.sensor.cemaden;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Painel público da CEMADEN ({@code resources.cemaden.gov.br/graficos/interativo/getJson2.php?uf=}), sem credencial:
 * uma linha por estação com a última leitura e os acumulados de 1 a 96 h. Não é série temporal.
 */
public interface CemadenAccumulationClient {

    /** Itens brutos da UF; falha da fonte vira {@link com.projeto.mapi.exception.SourceUnavailableException}. */
    List<JsonNode> fetchState(String uf);
}
