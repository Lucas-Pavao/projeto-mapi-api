package com.projeto.mapi.service;

public interface ApacHistoricalService {
    void ingestFullStateRainfall(int year);
    void ingestHistoricalRainfall(String stationCode, int year);
}
