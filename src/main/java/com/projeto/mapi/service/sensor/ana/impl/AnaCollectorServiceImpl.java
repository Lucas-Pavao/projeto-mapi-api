package com.projeto.mapi.service.sensor.ana.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.AnaStationDTO;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.sensor.ana.AnaCollectorService;
import com.projeto.mapi.service.sensor.ana.AnaDataClient;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.util.RmrFilter;
import com.projeto.mapi.util.SensorValueExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Substitui o antigo VirtualSensor(coletor_ana) do projeto Python: descobre dinamicamente as
 * estações ativas dentro do raio configurado (AnaDataClient.discoverStations — ver justificativa
 * lá: a antiga lista fixa de 8 códigos tinha 2 estações que não existem mais no inventário ativo
 * da ANA), busca as leituras de cada uma e alimenta o mesmo pipeline de negócio
 * (SensorService.processSensorMessage) que antes era acionado pelo listener MQTT.
 *
 * Diferença deliberada em relação ao Python: o coletor original só publicava a leitura mais
 * recente (dados[0]) a cada ciclo; aqui processamos TODOS os itens retornados na janela de busca
 * (Range Intervalo de busca) — o dedupe por (sensorId, timestamp) já existente em
 * SensorServiceImpl evita duplicar registros, e isso cobre eventuais leituras perdidas entre
 * execuções agendadas. A janela ANA é pequena (HORA_16 = poucas leituras/estação), diferente da
 * APAC, que retorna um histórico bem maior por chamada (ver ApacCollectorServiceImpl).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AnaCollectorServiceImpl implements AnaCollectorService {

    private final AnaDataClient anaDataClient;
    private final SensorService sensorService;
    private final ObjectMapper objectMapper;
    private final AppProperties appProperties;

    @Override
    public CollectionSummaryDTO collectAll() {
        AppProperties.Ana config = appProperties.getAna();
        if (!config.isEnabled()) {
            return CollectionSummaryDTO.empty();
        }

        List<AnaStationDTO> estacoes = anaDataClient.discoverStations();
        if (estacoes.isEmpty()) {
            log.warn("Nenhuma estação ANA descoberta dentro do raio configurado; pulando ciclo.");
            return CollectionSummaryDTO.empty();
        }

        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int processed = 0;
        int errors = 0;

        for (AnaStationDTO estacao : estacoes) {
            String nomeAmigavel = buildFriendlyName(estacao.name());
            try {
                List<JsonNode> medicoes = anaDataClient.fetchStationMeasurements(estacao.code(), hoje);
                for (JsonNode item : medicoes) {
                    if (!item.isObject()) continue;

                    ObjectNode envelope = objectMapper.createObjectNode();
                    envelope.setAll((ObjectNode) item);
                    envelope.put("id_sensor", "ANA-TELE-" + nomeAmigavel);

                    Double valorPrincipal = SensorValueExtractor.extract(item);
                    if (valorPrincipal != null) {
                        envelope.put("fog_valor_referencia", valorPrincipal);
                    }

                    sensorService.processSensorMessage(envelope.toString());
                    processed++;
                }
            } catch (Exception e) {
                errors++;
                log.error("Erro ao coletar estação ANA {} ({}): {}", estacao.code(), nomeAmigavel, e.getMessage());
            }
        }

        return new CollectionSummaryDTO(processed, errors);
    }

    private String buildFriendlyName(String rawName) {
        String normalizado = RmrFilter.normalize(rawName);
        if (normalizado.isBlank()) return "DESCONHECIDA";
        return normalizado.replaceAll("\\s+", "-");
    }
}
