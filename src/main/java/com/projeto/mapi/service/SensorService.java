package com.projeto.mapi.service;

import com.projeto.mapi.model.SensorData;
import java.util.List;

public interface SensorService {
    void processSensorMessage(String payload);
    List<SensorData> getAllLatestData();
    List<SensorData> getSensorHistory(String sensorId);
}
