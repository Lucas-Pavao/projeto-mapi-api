package com.projeto.mapi.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
        log.info("Received MQTT message: {}", payload);
        try {
            JsonNode root = objectMapper.readTree(payload);
            
            String sensorId = root.get("id_sensor").asText();
            
            // Lidar com valor_referencia que pode ser null
            double value = 0.0;
            if (root.has("fog_valor_referencia") && !root.get("fog_valor_referencia").isNull()) {
                value = root.get("fog_valor_referencia").asDouble();
            } else {
                // Se for null (comum na ANA), tentamos extrair do dados_originais
                value = extractValueFromOriginal(root.get("dados_originais"));
            }

            String batteryStatus = root.has("status_bateria") ? root.get("status_bateria").asText() : "N/A";
            String timestampStr = root.has("timestamp_coleta") ? root.get("timestamp_coleta").asText() : LocalDateTime.now().toString();
            String rawData = root.get("dados_originais").toString();
            
            LocalDateTime timestamp;
            try {
                timestamp = LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
            } catch (Exception e) {
                log.warn("Could not parse timestamp {}, using current time", timestampStr);
                timestamp = LocalDateTime.now();
            }

            SensorData data = SensorData.builder()
                    .sensorId(sensorId)
                    .value(value)
                    .batteryStatus(batteryStatus)
                    .rawData(rawData)
                    .timestamp(timestamp)
                    .build();
            
            // Tentar inferir unidade se possível (opcional)
            inferUnit(data, root.get("dados_originais"));

            sensorDataRepository.save(data);
            log.debug("Saved sensor data for {}", sensorId);
            
        } catch (Exception e) {
            log.error("Error processing JSON MQTT message: {}", payload, e);
            // Fallback para o formato antigo se necessário, ou apenas logar erro
        }
    }

    @Override
    public List<SensorData> getAllLatestData() {
        return sensorDataRepository.findAll(); // Simplificado para este exemplo, ideal seria um SELECT DISTINCT ou Query customizada
    }

    @Override
    public List<SensorData> getSensorHistory(String sensorId) {
        return sensorDataRepository.findBySensorIdOrderByTimestampDesc(sensorId);
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
            if (firstItem.has("Nivel_Adotado")) {
                data.setUnit("m");
                return;
            }
        }
        
        // Lógica para APAC
        if (originalData.has("chuva_acumulada") || originalData.has("precipitacao_acumulada") || originalData.has("Chuva_Adotada")) {
            data.setUnit("mm");
        } else if (originalData.has("Nivel_Adotado")) {
            data.setUnit("m");
        } else if (originalData.has("Vazao_Adotada")) {
            data.setUnit("m³/s");
        }
    }
}
