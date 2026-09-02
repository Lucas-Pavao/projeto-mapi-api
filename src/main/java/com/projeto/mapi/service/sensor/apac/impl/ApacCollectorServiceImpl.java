package com.projeto.mapi.service.sensor.apac.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.sensor.apac.ApacCollectorService;
import com.projeto.mapi.service.sensor.apac.ApacScrapeClient;
import com.projeto.mapi.service.sensor.SensorService;
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

        for (JsonNode item : ultimaLeituraPorEstacao.values()) {
            try {
                ObjectNode envelope = isCemaden ? parseCemaden(item, fonte) : parseMeteorologia24h(item, fonte);
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

        // O mesmo endpoint também publica estações fluviométricas com os thresholds oficiais de
        // alerta da APAC (ex.: Rio Duas Unas, em Jaboatão) — formato completamente diferente do de
        // estação meteorológica convencional. Sem este branch, essas leituras eram salvas
        // praticamente vazias (nenhum dos campos meteorológicos abaixo existe nelas).
        if (detalhes.has("river") || detalhes.has("alertLevel")) {
            return parseRiverLevel(item, detalhes, fonte);
        }

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

        // A API real chega a publicar até 6 níveis de umidade do solo (confirmado ao vivo), não só
        // 4 como o coletor Python original lia.
        ArrayNode umidadeSolo = envelope.putArray("umidade_solo");
        for (int nivel = 1; nivel <= 6; nivel++) {
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

    private ObjectNode parseRiverLevel(JsonNode item, JsonNode detalhes, String fonte) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("estacao_nome", item.path("Estação").asText("Desconhecida"));
        putIfPresent(envelope, "codigo", item.path("Codigo_gmmc"));

        String dataHora = item.path("Data-hora").asText(null);
        if (dataHora == null || dataHora.isBlank()) {
            String dataColeta = detalhes.path("dataColeta").asText("");
            String horaColeta = detalhes.path("horaColeta").asText("");
            dataHora = (dataColeta + " " + horaColeta).trim();
        }
        envelope.put("data_hora", dataHora.isBlank() ? "N/A" : dataHora);

        // Não há lat/lon nem "cidade" nesse formato — só nameStation ("CIDADE (DETALHE) (FONTE)").
        String nameStation = detalhes.path("nameStation").asText("");
        String municipio = nameStation.isBlank() ? "Não informada" : nameStation.split("\\(")[0].trim();
        envelope.put("municipio", municipio);

        if (detalhes.hasNonNull("river")) envelope.put("nome_rio", detalhes.path("river").asText());
        if (detalhes.hasNonNull("Nivel")) envelope.put("nivel_rio", detalhes.path("Nivel").asDouble());
        if (detalhes.hasNonNull("preAlertLevel")) envelope.put("nivel_pre_alerta", detalhes.path("preAlertLevel").asDouble());
        if (detalhes.hasNonNull("alertLevel")) envelope.put("nivel_alerta", detalhes.path("alertLevel").asDouble());
        if (detalhes.hasNonNull("floodLevel")) envelope.put("nivel_inundacao", detalhes.path("floodLevel").asDouble());
        if (detalhes.hasNonNull("chuva")) envelope.put("chuva_acumulada", detalhes.path("chuva").asDouble());

        envelope.put("tipo", "Fluviométrica");
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
