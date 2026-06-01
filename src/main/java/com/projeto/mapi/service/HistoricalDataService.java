package com.projeto.mapi.service;

public interface HistoricalDataService {
    void ingestHistoricalData(int years);
    void ingestPointHistory(String slug, int startYear, int endYear);
    void ingestHistoricalSensors(int years);
    void ingestApacHistoricalRainfall(String stationCode, int year);
    void ingestCivilDefenseData(String resourceId);
    void ingestCivilDefenseLastYears(int years);
    void alignFloodEventsToRainPeaks();
    java.util.List<com.projeto.mapi.dto.DataHealthReportDTO> checkDataIntegrity();
    void repairStationMappings();
    void ingestLocalArchives();
    void wipeDatabase();
}
