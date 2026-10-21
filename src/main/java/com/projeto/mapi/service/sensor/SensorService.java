package com.projeto.mapi.service.sensor;

import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.SensorData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface SensorService {
    void processSensorMessage(String payload);
    List<SensorResponseDTO> getAllLatestData();
    /**
     * Maior valor de {@code accumulatedPrecipitation} (mm) por pluviômetro nos últimos 60 min. Mesma leitura "chuva
     * acumulada" que o resto do sistema trata como chuva da hora (ver MapiServiceImpl, janelas de acumulado).
     */
    java.util.Map<String, Double> rainLastHourBySensor();
    /** Última leitura por sensor com {@code observedAt}, {@code ageSeconds} e {@code stale}; {@code type} (PRECIPITATION|RIVER_LEVEL) filtra. */
    List<SensorResponseDTO> getLatestWithFreshness(String type);
    List<SensorResponseDTO> getAllLatestData(java.time.LocalDateTime since);
    Page<SensorResponseDTO> getSensorHistory(String sensorId, Pageable pageable);
    SensorResponseDTO getLatestBySensorId(String sensorId);
    List<SensorResponseDTO> getSensorHistoryByCode(String code);
    SensorResponseDTO getLatestByCode(String code);
    List<String> getDistinctSensorIds();
    Page<SensorResponseDTO> getFullSensorInventory(Pageable pageable);
    List<com.projeto.mapi.model.FloodPoint> getFloodPointsCache();
}
