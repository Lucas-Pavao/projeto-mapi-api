package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.CollectionSummaryDTO;
import com.projeto.mapi.service.AnaCollectorService;
import com.projeto.mapi.service.AnaDataClient;
import com.projeto.mapi.service.SensorService;
import com.projeto.mapi.util.SensorValueExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Substitui o antigo VirtualSensor(coletor_ana) do projeto Python: itera as estações
 * configuradas, busca as leituras via AnaDataClient e alimenta o mesmo pipeline de negócio
 * (SensorService.processSensorMessage) que antes era acionado pelo listener MQTT.
 *
 * Diferença deliberada em relação ao Python: o coletor original só publicava a leitura mais
 * recente (dados[0]) a cada ciclo; aqui processamos TODOS os itens retornados na janela de busca
 * (Range Intervalo de busca) — o dedupe por (sensorId, timestamp) já existente em
 * SensorServiceImpl evita duplicar registros, e isso cobre eventuais leituras perdidas entre
 * execuções agendadas.
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

        String hoje = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        int processed = 0;
        int errors = 0;

        for (Map.Entry<String, String> station : config.getStations().entrySet()) {
            String codigo = station.getKey();
            String nomeAmigavel = station.getValue();
            try {
                List<JsonNode> medicoes = anaDataClient.fetchStationMeasurements(codigo, hoje);
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
                log.error("Erro ao coletar estação ANA {} ({}): {}", codigo, nomeAmigavel, e.getMessage());
            }
        }

        return new CollectionSummaryDTO(processed, errors);
    }
}
