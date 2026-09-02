package com.projeto.mapi.service.sensor.apac;

public interface ApacHistoricalService {
    void ingestFullStateRainfall(int year);
    void ingestHistoricalRainfall(String stationCode, int year);
}
