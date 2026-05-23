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
    private final com.projeto.mapi.service.TideService tideService;

    @Override
    @Transactional
    public void processSensorMessage(String payload) {
        log.info("Processando mensagem MQTT: {}", payload);
        try {
            // Tratar o caso de múltiplos objetos JSON concatenados no mesmo payload
            com.fasterxml.jackson.core.JsonParser parser = objectMapper.getFactory().createParser(payload);
            java.util.Iterator<JsonNode> it = objectMapper.readValues(parser, JsonNode.class);
            
            while (it.hasNext()) {
                JsonNode root = it.next();
                if (!root.has("id_sensor")) {
                    log.warn("Objeto JSON ignorado: 'id_sensor' não encontrado.");
                    continue;
                }
                String sensorId = root.get("id_sensor").asText();
                String batteryStatus = root.has("status_bateria") ? root.get("status_bateria").asText() : "N/A";
                processSinglePayload(root, sensorId, batteryStatus, root.toString());
            }
        } catch (Exception e) {
            log.error("Erro ao processar mensagem JSON do MQTT: {}", payload, e);
        }
    }

    private void processSinglePayload(JsonNode root, String sensorId, String batteryStatus, String payload) {
        String timestampStr = root.has("timestamp_coleta") ? root.get("timestamp_coleta").asText() : 
                           (root.has("data_hora") ? root.get("data_hora").asText() : 
                           (root.has("Data_Hora_Medicao") ? root.get("Data_Hora_Medicao").asText() : LocalDateTime.now().toString()));
        
        LocalDateTime timestamp = parseTimestamp(timestampStr);

        // Evitar duplicatas exatas
        if (sensorDataRepository.findBySensorIdAndTimestamp(sensorId, timestamp).isPresent()) {
            log.debug("Registro duplicado ignorado para {} em {}", sensorId, timestamp);
            return;
        }

        SensorData data = SensorData.builder()
                .sensorId(sensorId)
                .batteryStatus(batteryStatus)
                .rawData(payload)
                .timestamp(timestamp)
                .value(0.0) // Inicializar com 0.0 para evitar null
                .build();

        // Mapeamento de campos técnicos
        if (root.has("fog_valor_referencia") && !root.get("fog_valor_referencia").isNull()) data.setFogValueReference(root.get("fog_valor_referencia").asDouble());
        if (root.has("codigo")) data.setCode(root.get("codigo").asText());
        if (root.has("codigoestacao")) data.setCode(root.get("codigoestacao").asText());
        
        // Clima e Solo
        if (root.has("temperatura_ar") && !root.get("temperatura_ar").isNull()) data.setTemperature(root.get("temperatura_ar").asDouble());
        if (root.has("umidade_relativa") && !root.get("umidade_relativa").isNull()) data.setHumidity(root.get("umidade_relativa").asDouble());
        if (root.has("pressao_atmosferica") && !root.get("pressao_atmosferica").isNull()) data.setPressure(root.get("pressao_atmosferica").asDouble());
        if (root.has("velocidade_vento") && !root.get("velocidade_vento").isNull()) data.setWindSpeed(root.get("velocidade_vento").asDouble());
        if (root.has("direcao_vento") && !root.get("direcao_vento").isNull()) data.setWindDirection(root.get("direcao_vento").asText());
        if (root.has("radiacao_solar") && !root.get("radiacao_solar").isNull()) data.setSolarRadiation(root.get("radiacao_solar").asDouble());
        
        // Precipitação (vários nomes possíveis)
        if (root.has("precipitacao_acumulada") && !root.get("precipitacao_acumulada").isNull()) data.setAccumulatedPrecipitation(root.get("precipitacao_acumulada").asDouble());
        else if (root.has("chuva_acumulada") && !root.get("chuva_acumulada").isNull()) data.setAccumulatedPrecipitation(root.get("chuva_acumulada").asDouble());
        else if (root.has("Chuva_Adotada") && !root.get("Chuva_Adotada").isNull()) data.setAccumulatedPrecipitation(root.get("Chuva_Adotada").asDouble());

        if (root.has("umidade_solo")) data.setSoilHumidity(root.get("umidade_solo").toString());

        // ANA campos específicos
        if (root.has("Cota_Adotada") && !root.get("Cota_Adotada").isNull()) data.setWaterLevel(root.get("Cota_Adotada").asDouble());
        if (root.has("Vazao_Adotada") && !root.get("Vazao_Adotada").isNull()) data.setFlowRate(root.get("Vazao_Adotada").asDouble());
        if (root.has("Bacia_Nome")) data.setBasinName(root.get("Bacia_Nome").asText());

        // Atribuir valor principal para compatibilidade retroativa
        if (data.getFogValueReference() != null) data.setValue(data.getFogValueReference());
        else if (data.getAccumulatedPrecipitation() != null) data.setValue(data.getAccumulatedPrecipitation());
        else if (data.getWaterLevel() != null) data.setValue(data.getWaterLevel());

        extractMetadata(data, root);
        inferUnit(data, root);

        // Calcular altura da maré "naquele momento" se tivermos localização
        if (data.getLatitude() != null && data.getLongitude() != null) {
            try {
                Double tideHeight = tideService.getTideHeightAt(data.getLatitude(), data.getLongitude(), data.getTimestamp());
                data.setTideHeight(tideHeight);
            } catch (Exception e) {
                log.warn("Erro ao calcular altura da maré para o sensor {}: {}", sensorId, e.getMessage());
            }
        }

        try {
            sensorDataRepository.save(data);
            log.info("Dados do sensor {} salvos: timestamp={}", sensorId, timestamp);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Tentativa de salvar registro duplicado interceptada pelo banco: {} em {}", sensorId, timestamp);
        }
    }

    private void extractMetadata(SensorData data, JsonNode sourceNode) {
        // Campos APAC (minúsculos)
        if (sourceNode.has("estacao_nome")) data.setStationName(sourceNode.get("estacao_nome").asText());
        if (sourceNode.has("latitude") && !sourceNode.get("latitude").isNull()) data.setLatitude(sourceNode.get("latitude").asDouble());
        if (sourceNode.has("longitude") && !sourceNode.get("longitude").isNull()) data.setLongitude(sourceNode.get("longitude").asDouble());
        if (sourceNode.has("municipio")) data.setMunicipality(sourceNode.get("municipio").asText());
        
        // Campos ANA (PascalCase)
        if (sourceNode.has("Estacao_Nome") && data.getStationName() == null) data.setStationName(sourceNode.get("Estacao_Nome").asText());
        if (sourceNode.has("Latitude") && data.getLatitude() == null && !sourceNode.get("Latitude").isNull()) data.setLatitude(sourceNode.get("Latitude").asDouble());
        if (sourceNode.has("Longitude") && data.getLongitude() == null && !sourceNode.get("Longitude").isNull()) data.setLongitude(sourceNode.get("Longitude").asDouble());
        if (sourceNode.has("Municipio_Nome") && data.getMunicipality() == null) data.setMunicipality(sourceNode.get("Municipio_Nome").asText());

        if (sourceNode.has("tipo")) data.setType(sourceNode.get("tipo").asText());
        if (sourceNode.has("fonte")) data.setSource(sourceNode.get("fonte").asText());
        
        if (sourceNode.has("codigoestacao") && data.getSource() == null) {
            data.setSource("ANA");
        }
    }

    private LocalDateTime parseTimestamp(String timestampStr) {
        try {
            if (timestampStr.contains("T")) {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
            }
            // Tentar formatos comuns: yyyy-MM-dd HH:mm:ss
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSS][.SS][.S]");
            return LocalDateTime.parse(timestampStr, formatter);
        } catch (Exception e) {
            log.warn("Não foi possível processar o timestamp {}, usando hora atual", timestampStr);
            return LocalDateTime.now();
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

    @Override
    public SensorResponseDTO getLatestBySensorId(String sensorId) {
        return sensorDataRepository.findFirstBySensorIdOrderByTimestampDesc(sensorId)
                .map(this::convertToDTO)
                .orElse(null);
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
                .fogValueReference(data.getFogValueReference())
                .code(data.getCode())
                .temperature(data.getTemperature())
                .humidity(data.getHumidity())
                .pressure(data.getPressure())
                .windSpeed(data.getWindSpeed())
                .windDirection(data.getWindDirection())
                .solarRadiation(data.getSolarRadiation())
                .accumulatedPrecipitation(data.getAccumulatedPrecipitation())
                .soilHumidity(parseJsonSafe(data.getSoilHumidity()))
                .waterLevel(data.getWaterLevel())
                .flowRate(data.getFlowRate())
                .basinName(data.getBasinName())
                .tideHeight(data.getTideHeight())
                .build();
    }

    private JsonNode parseJsonSafe(String json) {
        if (json == null || json.isEmpty()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private void inferUnit(SensorData data, JsonNode node) {
        if (node.has("chuva_acumulada") || node.has("precipitacao_acumulada") || node.has("Chuva_Adotada")) {
            data.setUnit("mm");
        } else if (node.has("Cota_Adotada")) {
            data.setUnit("m");
        } else if (node.has("Vazao_Adotada")) {
            data.setUnit("m³/s");
        } else if (node.has("temperatura_ar")) {
            data.setUnit("°C");
        } else if (node.has("umidade_relativa")) {
            data.setUnit("%");
        }
    }
}
