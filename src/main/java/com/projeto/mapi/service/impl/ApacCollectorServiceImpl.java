package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.ApacCollectorService;
import com.projeto.mapi.service.ApacScrapeClient;
import com.projeto.mapi.service.SensorService;
import com.projeto.mapi.util.RmrFilter;
import com.projeto.mapi.util.SensorValueExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Substitui os antigos VirtualSensor(coletor_cemaden)/VirtualSensor(coletor_meteorologia) do
 * projeto Python: busca todas as estações de um endpoint, filtra pela Região Metropolitana do
 * Recife quando aplicável (RmrFilter, porta de src/utils/text_utils.py), monta o mesmo formato
 * de payload achatado que antes era publicado via MQTT e alimenta o pipeline de negócio
 * (SensorService.processSensorMessage).
 *
 * Nota de compatibilidade: SensorServiceImpl.processSinglePayload já força, para qualquer
 * sensorId que contenha "APAC" e tenha um "codigo" associado, o formato final
 * "APAC-PLUVIO-&lt;codigo&gt;" — isso vale tanto para Cemaden quanto para Meteorologia24h, e é um
 * comportamento pré-existente (o mesmo ocorria no fluxo MQTT antigo). O id_sensor construído
 * aqui só prevalece quando não há "codigo" na leitura.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ApacCollectorServiceImpl implements ApacCollectorService {

    private final ApacScrapeClient apacScrapeClient;
    private final SensorService sensorService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

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
        List<JsonNode> estacoes = apacScrapeClient.fetchRawStations(endpoint);
        int processed = 0;
        int errors = 0;

        for (JsonNode item : estacoes) {
            try {
                ObjectNode envelope = isCemaden ? parseCemaden(item, fonte) : parseMeteorologia24h(item, fonte);
                if (envelope == null) continue;

                String municipio = envelope.path("municipio").asText("");
                String estacaoNome = envelope.path("estacao_nome").asText("");
                if (rmrOnly && !RmrFilter.isRmr(municipio, estacaoNome)) {
                    continue;
                }

                envelope.put("id_sensor", buildSensorId(prefixoId, municipio, estacaoNome));

                Double valorPrincipal = SensorValueExtractor.extract(envelope);
                if (valorPrincipal != null) {
                    envelope.put("fog_valor_referencia", valorPrincipal);
                }

                sensorService.processSensorMessage(envelope.toString());
                processed++;
            } catch (Exception e) {
                errors++;
                log.error("Erro ao processar estação APAC ({}): {}", fonte, e.getMessage());
            }
        }

        return new CollectionSummaryDTO(processed, errors);
    }

    private ObjectNode parseCemaden(JsonNode item, String fonte) {
        JsonNode detalhes = parseDetalhes(item);
        if (detalhes == null) return null;

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("estacao_nome", item.path("Estação").asText("Desconhecida"));
        putIfPresent(envelope, "codigo", item.path("Codigo_gmmc"));
        putIfPresent(envelope, "data_hora", item.path("Data-hora"));
        putIfPresent(envelope, "latitude", detalhes.path("latitude"));
        putIfPresent(envelope, "longitude", detalhes.path("longitude"));
        envelope.put("municipio", textOrDefault(detalhes.path("cidade"), "Não informada"));
        envelope.put("chuva_acumulada", parseDoubleOrDefault(detalhes.path("chuva"), 0.0));
        envelope.put("tipo", textOrDefault(detalhes.path("tipo"), "Pluviométrica"));
        envelope.put("fonte", fonte);
        return envelope;
    }

    private ObjectNode parseMeteorologia24h(JsonNode item, String fonte) {
        JsonNode detalhes = parseDetalhes(item);
        if (detalhes == null) return null;

        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("estacao_nome", item.path("Estação").asText("Desconhecida"));
        putIfPresent(envelope, "codigo", item.path("Codigo_gmmc"));

        String dataHora = item.path("Data-hora").asText(null);
        if (dataHora == null || dataHora.isBlank()) {
            dataHora = detalhes.path("dataHora").asText("N/A");
        }
        envelope.put("data_hora", dataHora);

        putIfPresent(envelope, "latitude", detalhes.path("latitude"));
        putIfPresent(envelope, "longitude", detalhes.path("longitude"));

        String municipio = textOrDefault(detalhes.path("cidade"), null);
        if (municipio == null) {
            String nameStation = detalhes.path("nameStation").asText("");
            if (!nameStation.isBlank()) {
                municipio = nameStation.split("\\(")[0].trim();
            }
        }
        envelope.put("municipio", municipio == null || municipio.isBlank() ? "Não informada" : municipio);

        putIfPresentEither(envelope, "temperatura_ar", detalhes, "temperatura_ar", "temperatura_inst");
        putIfPresentEither(envelope, "umidade_relativa", detalhes, "umidade_relativa", "umidade_inst");
        putIfPresentEither(envelope, "pressao_atmosferica", detalhes, "pressao_atmosferica", "pressao_inst");
        putIfPresentEither(envelope, "velocidade_vento", detalhes, "velocidade_vento", "vento_velocidade");
        putIfPresentEither(envelope, "direcao_vento", detalhes, "direcao_vento", "vento_direcao");
        putIfPresentEither(envelope, "radiacao_solar", detalhes, "radiacao_solar", "radiacao_solar_global");

        double precipitacao = detalhes.hasNonNull("precipitacao_acumulada")
                ? detalhes.path("precipitacao_acumulada").asDouble(0)
                : detalhes.path("precipitacao_xx_00").asDouble(0);
        envelope.put("precipitacao_acumulada", precipitacao);

        ArrayNode umidadeSolo = envelope.putArray("umidade_solo");
        for (int nivel = 1; nivel <= 4; nivel++) {
            JsonNode valor = detalhes.hasNonNull("umidade_solo_nivel" + nivel)
                    ? detalhes.path("umidade_solo_nivel" + nivel)
                    : detalhes.path("umidade_solo_nivel" + nivel + "_media");
            if (valor.isMissingNode() || valor.isNull()) {
                umidadeSolo.addNull();
            } else {
                umidadeSolo.add(valor.asText());
            }
        }

        envelope.put("tipo", textOrDefault(detalhes.path("tipo"), "Mista/N/A"));
        envelope.put("fonte", fonte);
        return envelope;
    }

    private JsonNode parseDetalhes(JsonNode item) {
        String detalhesStr = item.path("Dados_completos").asText(null);
        if (detalhesStr == null || detalhesStr.isBlank()) return null;
        try {
            return objectMapper.readTree(detalhesStr);
        } catch (Exception e) {
            log.debug("Dados_completos inválido, ignorando item: {}", e.getMessage());
            return null;
        }
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

    private void putIfPresent(ObjectNode target, String key, JsonNode value) {
        if (value != null && !value.isMissingNode() && !value.isNull()) {
            target.put(key, value.asText());
        }
    }

    private void putIfPresentEither(ObjectNode target, String key, JsonNode source, String primaryKey, String fallbackKey) {
        JsonNode value = source.hasNonNull(primaryKey) ? source.path(primaryKey) : source.path(fallbackKey);
        if (!value.isMissingNode() && !value.isNull()) {
            target.put(key, value.asDouble());
        }
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        String text = node.asText();
        return text.isBlank() ? fallback : text;
    }

    private double parseDoubleOrDefault(JsonNode node, double fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) return fallback;
        try {
            return node.isNumber() ? node.asDouble() : Double.parseDouble(node.asText());
        } catch (Exception e) {
            return fallback;
        }
    }
}
