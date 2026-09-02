package com.projeto.mapi.service.sensor.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.FloodPoint;
import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.repository.FloodPointRepository;
import com.projeto.mapi.repository.SensorDataRepository;
import com.projeto.mapi.service.sensor.SensorService;
import com.projeto.mapi.util.GeoUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SensorServiceImpl implements SensorService {
    private final SensorDataRepository sensorDataRepository;
    private final FloodPointRepository floodPointRepository;
    private final ObjectMapper objectMapper;
    private final com.projeto.mapi.service.tide.TideService tideService;

    @Override
    @Transactional
    @org.springframework.scheduling.annotation.Async("taskExecutor")
    public void processSensorMessage(String payload) {
        log.info("Processando payload de sensor: {}", payload);
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
            log.error("Erro ao processar payload JSON de sensor: {}", payload, e);
        }
    }

    private void processSinglePayload(JsonNode root, String sensorId, String batteryStatus, String payload) {
        String timestampStr = null;
        if (root.has("timestamp_coleta")) timestampStr = root.get("timestamp_coleta").asText();
        else if (root.has("data_hora")) timestampStr = root.get("data_hora").asText();
        else if (root.has("Data_Hora_Medicao")) timestampStr = root.get("Data_Hora_Medicao").asText();
        else if (root.has("data_hora_medicao")) timestampStr = root.get("data_hora_medicao").asText();
        else if (root.has("horario")) timestampStr = root.get("horario").asText();
        
        LocalDateTime timestamp = parseTimestamp(timestampStr);

        SensorData temp = new SensorData();
        extractMetadata(temp, root); // Extrair lat/lon antes para validar proximidade
        
        // Identificadores Únicos (GMMC / Código da Estação)
        if (root.has("codigo")) temp.setCode(root.get("codigo").asText());
        else if (root.has("codigoestacao")) temp.setCode(root.get("codigoestacao").asText());
        else if (root.has("cod_estacao")) temp.setCode(root.get("cod_estacao").asText());
        else if (root.has("cod_estação")) temp.setCode(root.get("cod_estação").asText());

        String finalSensorId = sensorId;
        String code = temp.getCode();
        boolean isRiverStation = root.has("nome_rio");

        // PADRONIZAÇÃO: Se for um sensor da APAC (identificado pelo código ou ID atual), força o
        // formato final por código. Estações fluviométricas (nível de rio com alerta oficial da
        // APAC, ex. Rio Duas Unas) usam prefixo próprio — misturá-las com pluviômetros no mesmo
        // "APAC-PLUVIO-" seria enganoso, já que são grandezas e fontes de alerta bem diferentes.
        if (code != null && !code.isBlank() && (sensorId.contains("APAC") || sensorId.startsWith("26"))) {
            finalSensorId = (isRiverStation ? "APAC-RIO-" : "APAC-PLUVIO-") + code;
        }

        // Fallback de coordenadas: alguns formatos da APAC (estações "[APAC]" genéricas e as
        // fluviométricas do meteorologia24h) não trazem lat/lon no payload em tempo real. Reusa o
        // mesmo registro estático já usado pela ingestão histórica (ApacStationRegistry) em vez de
        // perder o vínculo geográfico com os pontos de monitoramento nesses casos.
        if ((temp.getLatitude() == null || temp.getLongitude() == null) && code != null) {
            com.projeto.mapi.util.ApacStationRegistry.StationMetadata meta = com.projeto.mapi.util.ApacStationRegistry.getMetadata(code);
            if (meta != null) {
                temp.setLatitude(meta.getLatitude());
                temp.setLongitude(meta.getLongitude());
            }
        }

        // Proximidade com pontos de alagamento para vínculo em mão dupla (Dual-Link)
        if (temp.getLatitude() != null && temp.getLongitude() != null) {
            List<FloodPoint> points = getFloodPointsCache();

            // Regra de 3km para auto-vínculo
            List<FloodPoint> nearbyPoints = points.stream()
                    .filter(fp -> GeoUtils.calculateDistance(temp.getLatitude(), temp.getLongitude(), fp.getLatitude(), fp.getLongitude()) <= 3.0)
                    .toList();

            for (FloodPoint fp : nearbyPoints) {
                boolean pointUpdated = false;
                // Vínculo de Chuva
                boolean isRain = root.has("precipitacao_acumulada") || root.has("chuva_acumulada") || root.has("Chuva_Adotada");
                if (isRain && !fp.getPluviometerStationIds().contains(finalSensorId)) {
                    log.info("---- [Dual-Link] Vinculando novo pluviômetro {} ao ponto {}", finalSensorId, fp.getSlug());
                    fp.getPluviometerStationIds().add(finalSensorId);
                    pointUpdated = true;
                }
                // Vínculo de Nível de Rio
                boolean isRiver = root.has("Cota_Adotada") || root.has("nivel_rio");
                if (isRiver && !fp.getRiverLevelStationIds().contains(finalSensorId)) {
                    log.info("---- [Dual-Link] Vinculando novo nível de rio {} ao ponto {}", finalSensorId, fp.getSlug());
                    fp.getRiverLevelStationIds().add(finalSensorId);
                    pointUpdated = true;
                }
                
                if (pointUpdated) {
                    floodPointRepository.save(fp);
                }
            }
        } else {
            // Estação meteorológica (sem coordenadas), vincular a todos os pontos
            List<FloodPoint> points = getFloodPointsCache();
            boolean updatedAny = false;
            for (FloodPoint fp : points) {
                if (fp.getWeatherStationIds() == null) {
                    fp.setWeatherStationIds(new java.util.HashSet<>());
                }
                if (!fp.getWeatherStationIds().contains(finalSensorId)) {
                    fp.getWeatherStationIds().add(finalSensorId);
                    floodPointRepository.save(fp);
                    updatedAny = true;
                }
            }
            if (updatedAny) {
                log.info("---- Estação meteorológica {} vinculada a todos os pontos de monitoramento.", finalSensorId);
            }
        }

        // Evitar duplicatas exatas usando o sensorId PADRONIZADO
        if (sensorDataRepository.findBySensorIdAndTimestamp(finalSensorId, timestamp).isPresent()) {
            log.debug("Registro duplicado ignorado para {} em {}", finalSensorId, timestamp);
            return;
        }

        SensorData data = SensorData.builder()
                .sensorId(finalSensorId) // SEMPRE o sensorId padronizado
                .batteryStatus(batteryStatus)
                .rawData(payload)
                .timestamp(timestamp)
                .value(0.0) // Inicializar com 0.0 para evitar null
                .latitude(temp.getLatitude())
                .longitude(temp.getLongitude())
                .stationName(temp.getStationName())
                .municipality(temp.getMunicipality())
                .code(code)
                .build();

        // Mapeamento de campos técnicos
        if (root.has("fog_valor_referencia") && !root.get("fog_valor_referencia").isNull()) data.setFogValueReference(root.get("fog_valor_referencia").asDouble());
        
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

        // APAC - estações fluviométricas de nível de rio com alertas oficiais (meteorologia24h)
        if (root.has("nivel_rio") && !root.get("nivel_rio").isNull()) data.setWaterLevel(root.get("nivel_rio").asDouble());
        if (root.has("nome_rio")) data.setRiverName(root.get("nome_rio").asText());
        if (root.has("nivel_pre_alerta") && !root.get("nivel_pre_alerta").isNull()) data.setRiverPreAlertLevel(root.get("nivel_pre_alerta").asDouble());
        if (root.has("nivel_alerta") && !root.get("nivel_alerta").isNull()) data.setRiverAlertLevel(root.get("nivel_alerta").asDouble());
        if (root.has("nivel_inundacao") && !root.get("nivel_inundacao").isNull()) data.setRiverFloodLevel(root.get("nivel_inundacao").asDouble());

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
                log.warn("Erro ao calcular altura da maré para o sensor {}: {}", finalSensorId, e.getMessage());
            }
        }

        try {
            sensorDataRepository.save(data);
            log.info("Dados do sensor {} salvos: timestamp={}", finalSensorId, timestamp);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.warn("Tentativa de salvar registro duplicado interceptada pelo banco: {} em {}", finalSensorId, timestamp);
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
        if (timestampStr == null || timestampStr.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            if (timestampStr.contains("T")) {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ISO_DATE_TIME);
            }
            if (timestampStr.contains("/")) {
                return LocalDateTime.parse(timestampStr, DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"));
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
        // Por padrão, retorna apenas dados das últimas 24 horas para evitar lixo histórico
        return getAllLatestData(LocalDateTime.now().minusHours(24));
    }

    @Override
    public List<SensorResponseDTO> getAllLatestData(LocalDateTime since) {
        return sensorDataRepository.findAllLatest(since).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    public Page<SensorResponseDTO> getSensorHistory(String sensorId, Pageable pageable) {
        return sensorDataRepository.findBySensorIdOrderByTimestampDesc(sensorId, pageable)
                .map(this::convertToDTO);
    }

    @Override
    public SensorResponseDTO getLatestBySensorId(String sensorId) {
        return sensorDataRepository.findFirstBySensorIdOrderByTimestampDesc(sensorId)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    public List<SensorResponseDTO> getSensorHistoryByCode(String code) {
        return sensorDataRepository.findByCodeOrderByTimestampDesc(code).stream()
                .map(this::convertToDTO)
                .toList();
    }

    @Override
    public SensorResponseDTO getLatestByCode(String code) {
        return sensorDataRepository.findFirstByCodeOrderByTimestampDesc(code)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    public List<String> getDistinctSensorIds() {
        return sensorDataRepository.findDistinctSensorIds();
    }

    @Override
    public Page<SensorResponseDTO> getFullSensorInventory(Pageable pageable) {
        return sensorDataRepository.findDistinctSensorsWithMetadata(pageable)
                .map(this::convertToDTO);
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
                .riverName(data.getRiverName())
                .riverPreAlertLevel(data.getRiverPreAlertLevel())
                .riverAlertLevel(data.getRiverAlertLevel())
                .riverFloodLevel(data.getRiverFloodLevel())
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

    @Override
    @org.springframework.cache.annotation.Cacheable("floodPoints")
    public List<FloodPoint> getFloodPointsCache() {
        log.info("Buscando pontos de alagamento do banco de dados e populando o cache.");
        return floodPointRepository.findAll();
    }
}
