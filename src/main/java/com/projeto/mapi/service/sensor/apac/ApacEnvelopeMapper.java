package com.projeto.mapi.service.sensor.apac;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Converte um item bruto dos endpoints "cemaden"/"meteorologia24h" da APAC no envelope achatado do coletor
 * (mesmo formato que alimenta {@code SensorService} e o staging do pipeline). Extraído de
 * {@code ApacCollectorServiceImpl} para que o adaptador de carga do pipeline reuse a mesma montagem
 * sem depender do coletor (evita ciclo de beans).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ApacEnvelopeMapper {

    private final ObjectMapper mapper;

    public ObjectNode cemaden(JsonNode item, String fonte) {
        JsonNode detalhes = parseDetalhes(item);
        if (detalhes == null) return null;

        ObjectNode envelope = mapper.createObjectNode();
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

    public ObjectNode meteorologia24h(JsonNode item, String fonte) {
        JsonNode detalhes = parseDetalhes(item);
        if (detalhes == null) return null;

        // O mesmo endpoint também publica estações fluviométricas com os thresholds oficiais de
        // alerta da APAC (ex.: Rio Duas Unas, em Jaboatão) — formato completamente diferente do de
        // estação meteorológica convencional. Sem este branch, essas leituras eram salvas
        // praticamente vazias (nenhum dos campos meteorológicos abaixo existe nelas).
        if (detalhes.has("river") || detalhes.has("alertLevel")) {
            return riverLevel(item, detalhes, fonte);
        }

        ObjectNode envelope = mapper.createObjectNode();
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

    private ObjectNode riverLevel(JsonNode item, JsonNode detalhes, String fonte) {
        ObjectNode envelope = mapper.createObjectNode();
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
            return mapper.readTree(detalhesStr);
        } catch (Exception e) {
            log.debug("Dados_completos inválido, ignorando item: {}", e.getMessage());
            return null;
        }
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
