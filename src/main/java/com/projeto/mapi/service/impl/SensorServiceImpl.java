package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.SensorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorServiceImpl implements SensorService {
    private final SensorDataRepository sensorDataRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void processSensorMessage(String payload) {
        log.info("Processando mensagem MQTT: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);
            
            if (!root.has("id_sensor")) {
                log.warn("Mensagem ignorada: 'id_sensor' não encontrado. Payload: {}", payload);
                return;
            }

            String sensorId = root.get("id_sensor").asText();
            String batteryStatus = root.has("status_bateria") ? root.get("status_bateria").asText() : "N/A";
            
            // Se tiver 'items', processar cada um individualmente (Padrão ANA)
            if (root.has("dados_originais") && root.get("dados_originais").has("items") && root.get("dados_originais").get("items").isArray()) {
                JsonNode items = root.get("dados_originais").get("items");
                log.info("Processando {} itens para o sensor {}", items.size(), sensorId);
                for (JsonNode item : items) {
                    processSingleItem(sensorId, batteryStatus, item, item.toString());
                }
            } else {
                // Processamento padrão (Padrão APAC ou Simples)
                processSinglePayload(root, sensorId, batteryStatus, payload);
            }
            
        } catch (Exception e) {
            log.error("Erro ao processar mensagem JSON do MQTT: {}", payload, e);
        }
    }

    private void processSinglePayload(JsonNode root, String sensorId, String batteryStatus, String payload) {
        double value = 0.0;
        if (root.has("fog_valor_referencia") && !root.get("fog_valor_referencia").isNull()) {
            value = root.get("fog_valor_referencia").asDouble();
        } else if (root.has("dados_originais")) {
            value = extractValueFromOriginal(root.get("dados_originais"));
        }

        String timestampStr = root.has("timestamp_coleta") ? root.get("timestamp_coleta").asText() : LocalDateTime.now().toString();
        String rawData = root.has("dados_originais") ? root.get("dados_originais").toString() : payload;
        
        saveIfNew(sensorId, value, batteryStatus, timestampStr, rawData, root.get("dados_originais"));
    }

    private void processSingleItem(String sensorId, String batteryStatus, JsonNode item, String rawData) {
        double value = 0.0;
        if (item.has("Chuva_Adotada") && !item.get("Chuva_Adotada").isNull()) value = item.get("Chuva_Adotada").asDouble();
        else if (item.has("Cota_Adotada") && !item.get("Cota_Adotada").isNull()) value = item.get("Cota_Adotada").asDouble();
        else if (item.has("Nivel_Adotado") && !item.get("Nivel_Adotado").isNull()) value = item.get("Nivel_Adotado").asDouble();

        String timestampStr = item.has("Data_Hora_Medicao") ? item.get("Data_Hora_Medicao").asText() : LocalDateTime.now().toString();
        
        saveIfNew(sensorId, value, batteryStatus, timestampStr, rawData, item);
    }

    private void saveIfNew(String sensorId, double value, String batteryStatus, String timestampStr, String rawData, JsonNode sourceNode) {
        LocalDateTime timestamp = parseTimestamp(timestampStr);
        
        // Evitar duplicatas exatas (mesmo sensor e mesmo timestamp)
        if (sensorDataRepository.findBySensorIdAndTimestamp(sensorId, timestamp).isPresent()) {
            log.debug("Registro duplicado ignorado para {} em {}", sensorId, timestamp);
            return;
        }

        SensorData data = SensorData.builder()
                .sensorId(sensorId)
                .value(value)
                .batteryStatus(batteryStatus)
                .rawData(rawData)
                .timestamp(timestamp)
                .build();
        
        if (sourceNode != null) {
            inferUnit(data, sourceNode);
            extractMetadata(data, sourceNode);
        }

        try {
            sensorDataRepository.save(data);
            log.info("Dados do sensor {} salvos: valor={}, timestamp={}", sensorId, value, timestamp);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Tentativa de salvar registro duplicado interceptada pelo banco: {} em {}", sensorId, timestamp);
        }
    }

    private void extractMetadata(SensorData data, JsonNode sourceNode) {
        if (sourceNode.has("estacao_nome")) data.setStationName(sourceNode.get("estacao_nome").asText());
        if (sourceNode.has("latitude") && !sourceNode.get("latitude").isNull()) data.setLatitude(sourceNode.get("latitude").asDouble());
        if (sourceNode.has("longitude") && !sourceNode.get("longitude").isNull()) data.setLongitude(sourceNode.get("longitude").asDouble());
        if (sourceNode.has("municipio")) data.setMunicipality(sourceNode.get("municipio").asText());
        if (sourceNode.has("tipo")) data.setType(sourceNode.get("tipo").asText());
        if (sourceNode.has("fonte")) data.setSource(sourceNode.get("fonte").asText());
        
        // Se for ANA, os metadados podem estar em outros campos
        if (sourceNode.has("codigoestacao")) {
            if (data.getStationName() == null) data.setStationName("Estação " + sourceNode.get("codigoestacao").asText());
            if (data.getSource() == null) data.setSource("ANA");
        }
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            // Tentar ISO
            return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e1) {
            try {
                // Tentar formato comum da ANA: yyyy-MM-dd HH:mm:ss.S
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]");
                return LocalDateTime.parse(timestampStr, formatter);
            } catch (Exception e2) {
                log.warn("Não foi possível processar o timestamp {}, usando hora atual", timestampStr);
                return LocalDateTime.now();
            }
        }
    }

    @Override
    public List<SensorResponseDTO> getAllLatestData() {
        return sensorDataRepository.findAllLatest().stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    public List<SensorResponseDTO> getSensorHistory(String sensorId) {
        return sensorDataRepository.findBySensorIdOrderByTimestampDesc(sensorId).stream()
                .map(this::convertToDTO)
                .toList();
    }

    private SensorResponseDTO convertToDTO(SensorData data) {
        return SensorResponseDTO.builder()
                .id(data.getId())
                .sensorId(data.getSensorId())
                .value(data.getValue())
                .unit(data.getUnit())
                .batteryStatus(data.getBatteryStatus())
                .timestamp(data.getTimestamp())
                .stationName(data.getStationName())
                .latitude(data.getLatitude())
                .longitude(data.getLongitude())
                .municipality(data.getMunicipality())
                .type(data.getType())
                .source(data.getSource())
                .rawData(data.getRawData())
                .build();
    }

    private double extractValueFromOriginal(JsonNode originalData) {
        try {
            // Se for ANA, os dados estão dentro de "items" (lista)
            if (originalData.has("items") && originalData.get("items").isArray() && originalData.get("items").size() > 0) {
                JsonNode firstItem = originalData.get("items").get(0);
                if (firstItem.has("Chuva_Adotada")) return firstItem.get("Chuva_Adotada").asDouble();
                if (firstItem.has("Nivel_Adotado")) return firstItem.get("Nivel_Adotado").asDouble();
            }
            // Se for APAC Direto
            if (originalData.has("chuva_acumulada")) return originalData.get("chuva_acumulada").asDouble();
            if (originalData.has("precipitacao_acumulada")) return originalData.get("precipitacao_acumulada").asDouble();
        } catch (Exception e) {
            log.warn("Could not extract value from original data");
        }
        return 0.0;
    }

    private void inferUnit(SensorData data, JsonNode originalData) {
        // Lógica para ANA (dentro de items)
        if (originalData.has("items") && originalData.get("items").isArray() && originalData.get("items").size() > 0) {
            JsonNode firstItem = originalData.get("items").get(0);
            if (firstItem.has("Chuva_Adotada")) {
                data.setUnit("mm");
                return;
            }
            if (firstItem.has("Nivel_Adotado") || firstItem.has("Cota_Adotada")) {
                data.setUnit("m");
                return;
            }
        }
        
        // Lógica para APAC ou itens individuais
        if (originalData.has("chuva_acumulada") || originalData.has("precipitacao_acumulada") || originalData.has("Chuva_Adotada")) {
            data.setUnit("mm");
        } else if (originalData.has("Nivel_Adotado") || originalData.has("Cota_Adotada") || originalData.has("Cota")) {
            data.setUnit("m");
        } else if (originalData.has("Vazao_Adotada") || originalData.has("Vazao")) {
            data.setUnit("m³/s");
        }
    }
}
