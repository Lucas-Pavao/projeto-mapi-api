package com.projeto.mapi.service.sensor.apac;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * Cliente de scraping dos endpoints "cemaden"/"meteorologia24h" da APAC, porta do coletor
 * Python de referência (projeto-mapi/src/collectors/base_collector.py). A resposta é HTML cujo
 * &lt;body&gt; contém uma string JSON crua (não é uma API JSON formal).
 */
public interface ApacScrapeClient {
    /**
     * @param endpointPath "cemaden" ou "meteorologia24h" (ver AppProperties.Apac)
     * @return itens brutos, cada um com os campos "Estação", "Codigo_gmmc", "Data-hora" e
     *         "Dados_completos" (string JSON aninhada a ser parseada pelo chamador).
     */
    List<JsonNode> fetchRawStations(String endpointPath);
}
