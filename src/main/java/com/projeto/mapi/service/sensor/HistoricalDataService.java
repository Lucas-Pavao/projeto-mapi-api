package com.projeto.mapi.service.sensor;

public interface HistoricalDataService {
    // Sincronização TOTAL: clima + ANA + APAC + Defesa Civil (sem realinhar horários dos eventos).
    void ingestHistoricalData(int years);

    // Só clima (Open-Meteo) para todos os pontos — antes o endpoint "/historical-weather" dizia
    // fazer isso mas na verdade chamava ingestHistoricalData (sincronização total), duplicando
    // "/historical-full-sync" de forma enganosa.
    void ingestHistoricalWeather(int years);

    void ingestPointHistory(String slug, int startYear, int endYear);
    void ingestHistoricalSensors(int years);
    void ingestApacFullStateRainfall(int year);
    void ingestApacHistoricalRainfall(String stationCode, int year);
    void ingestCivilDefenseData(int years);
    void ingestCivilDefenseLastYears(int years);
    java.util.List<com.projeto.mapi.dto.DataHealthReportDTO> checkDataIntegrity();
    void repairStationMappings();
    void wipeDatabase();
}
