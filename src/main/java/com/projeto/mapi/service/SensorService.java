package com.projeto.mapi.service;

import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.model.SensorData;
import java.util.List;

public interface SensorService {
    void processSensorMessage(String payload);
    List<SensorResponseDTO> getAllLatestData();
    List<SensorResponseDTO> getSensorHistory(String sensorId);
    SensorResponseDTO getLatestBySensorId(String sensorId);
    List<SensorResponseDTO> getSensorHistoryByCode(String code);
    SensorResponseDTO getLatestByCode(String code);
    List<String> getDistinctSensorIds();
}
