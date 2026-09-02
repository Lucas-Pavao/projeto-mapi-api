package com.projeto.mapi.service.sensor.ana;

public interface AnaHistoricalService {
    void ingestHistoricalSensorData(String stationCode, int years);
}
