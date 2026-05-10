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
            double value = root.get("fog_valor_referencia").asDouble();
            String batteryStatus = root.get("status_bateria").asText();
            String timestampStr = root.get("timestamp_coleta").asText();
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

    private void inferUnit(SensorData data, JsonNode originalData) {
        // Lógica simples baseada nos campos presentes no dados_originais
        if (originalData.has("chuva_acumulada") || originalData.has("precipitacao_acumulada") || originalData.has("Chuva_Adotada")) {
            data.setUnit("mm");
        } else if (originalData.has("Nivel_Adotado")) {
            data.setUnit("m");
        } else if (originalData.has("Vazao_Adotada")) {
            data.setUnit("m³/s");
        }
    }
}
