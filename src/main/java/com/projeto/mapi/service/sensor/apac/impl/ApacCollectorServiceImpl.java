package com.projeto.mapi.service.sensor.apac.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.sensor.apac.ApacCollectorService;
import com.projeto.mapi.service.sensor.apac.ApacEnvelopeMapper;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.service.ingestion.live.LiveSensorStagingService;
import com.projeto.mapi.util.RmrFilter;
import com.projeto.mapi.util.SensorValueExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Substitui os antigos VirtualSensor(coletor_cemaden)/VirtualSensor(coletor_meteorologia) do
 * projeto Python: busca todas as estações de um endpoint, filtra pela Região Metropolitana do
 * Recife quando aplicável (RmrFilter, porta de src/utils/text_utils.py), monta o mesmo formato
 * de payload achatado que antes era publicado via MQTT e alimenta o pipeline de negócio
 * (SensorService.processSensorMessage).
 *
 * Nota de compatibilidade: SensorServiceImpl.processSinglePayload já força, para qualquer
 * sensorId que contenha "APAC" e tenha um "codigo" associado, o formato final por código —
 * "APAC-PLUVIO-&lt;codigo&gt;" para leituras normais (Cemaden/Meteorologia24h) e
 * "APAC-RIO-&lt;codigo&gt;" especificamente para as estações fluviométricas com alerta oficial
 * (ver parseRiverLevel/campo "nome_rio"), que são uma grandeza e fonte de alerta diferentes de um
 * pluviômetro. O id_sensor construído aqui só prevalece quando não há "codigo" na leitura.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApacCollectorServiceImpl implements ApacCollectorService {

    private final ApacScrapeClient apacScrapeClient;
    private final SensorService sensorService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;
    private final LiveSensorStagingService staging;
    private final ApacEnvelopeMapper envelopes;

    @Override
    public CollectionSummaryDTO collectCemaden() {
        AppProperties.Apac config = appProperties.getApac();
        if (!config.isEnabled()) return CollectionSummaryDTO.empty();

        return collect(config.getCemadenEndpoint(), "APAC-PLUVIO", "APAC/Cemaden", config.isCemadenRmrOnly(), true);
    }

    @Override
    public CollectionSummaryDTO collectMeteorologia24h() {
        AppProperties.Apac config = appProperties.getApac();
        if (!config.isEnabled()) return CollectionSummaryDTO.empty();

        return collect(config.getMeteorologiaEndpoint(), "APAC-METEO", "APAC/Meteorologia24h", config.isMeteorologiaRmrOnly(), false);
    }

    private CollectionSummaryDTO collect(String endpoint, String prefixoId, String fonte, boolean rmrOnly, boolean isCemaden) {
        List<JsonNode> estacoes;
        try {
            estacoes = apacScrapeClient.fetchRawStations(endpoint);
        } catch (Exception e) {
            log.error("Fonte {} indisponível: {}", fonte, e.getMessage());
            return CollectionSummaryDTO.unavailable();
        }

        // O endpoint devolve um histórico rolante por estação (chegamos a ver ~150 leituras para
        // uma única estação no Cemaden, ~8.700 itens no total por fetch), não um snapshot atual.
        // Processar tudo a cada ciclo geraria milhares de consultas de dedupe redundantes no banco
        // a cada poucos minutos — mantemos só a leitura mais recente por estação por ciclo, no
        // mesmo espírito do VirtualSensor original (que só publicava dados[0]).
        Map<String, JsonNode> ultimaLeituraPorEstacao = new LinkedHashMap<>();
        for (JsonNode item : estacoes) {
            String codigo = item.path("Codigo_gmmc").asText(null);
            if (codigo == null || codigo.isBlank()) continue;
            JsonNode atual = ultimaLeituraPorEstacao.get(codigo);
            if (atual == null || item.path("Data-hora").asText("").compareTo(atual.path("Data-hora").asText("")) > 0) {
                ultimaLeituraPorEstacao.put(codigo, item);
            }
        }

        int processed = 0;
        int errors = 0;
        List<JsonNode> staged = new java.util.ArrayList<>();

        for (JsonNode item : ultimaLeituraPorEstacao.values()) {
            try {
                ObjectNode envelope = isCemaden ? envelopes.cemaden(item, fonte) : envelopes.meteorologia24h(item, fonte);
                if (envelope == null) continue;

                String municipio = envelope.path("municipio").asText("");
                String estacaoNome = envelope.path("estacao_nome").asText("");
                if (rmrOnly && !RmrFilter.isRmr(municipio, estacaoNome)) {
                    continue;
                }

                envelope.put("id_sensor", buildSensorId(prefixoId, municipio, estacaoNome));

                // Estações fluviométricas (parseRiverLevel) já têm seu valor principal (nível do
                // rio) mapeado diretamente para waterLevel em SensorServiceImpl — não usar o
                // extrator genérico aqui, ou uma chuva incidental de 0mm na mesma leitura venceria
                // erroneamente a prioridade de "value" sobre o nível do rio.
                if (!envelope.has("nome_rio")) {
                    Double valorPrincipal = SensorValueExtractor.extract(envelope);
                    if (valorPrincipal != null) {
                        envelope.put("fog_valor_referencia", valorPrincipal);
                    }
                }

                sensorService.processSensorMessage(envelope.toString());
                staged.add(envelope);
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("Erro ao processar estação APAC ({}): {}", fonte, e.getMessage());
            }
        }

        // Pipeline novo (ADR 0006): meteorologia24h e Cemaden com fuso UNVERIFIED => quarentena TZ_UNVERIFIED.
        staging.stage(isCemaden ? "CEMADEN" : "APAC_METEO24H", staged);

        return new CollectionSummaryDTO(processed, errors);
    }

    private String buildSensorId(String prefixoId, String municipio, String estacaoNome) {
        String cidadeId = firstWordOrDefault(RmrFilter.normalize(municipio), "RMR");
        String nomeLimpo = estacaoNome.replace("[APAC]", "").replace("[CEMADEN]", "")
                .replace("[", "").replace("]", "").trim();
        String nomeId = firstWordOrDefault(RmrFilter.normalize(nomeLimpo), "ESTACAO");
        return prefixoId + "-" + cidadeId + "-" + nomeId;
    }

    private String firstWordOrDefault(String normalized, String fallback) {
        if (normalized == null || normalized.isBlank() || normalized.equals("NAO INFORMADA")) return fallback;
        String[] parts = normalized.split("\\s+");
        return parts.length > 0 && !parts[0].isBlank() ? parts[0] : fallback;
    }

}
