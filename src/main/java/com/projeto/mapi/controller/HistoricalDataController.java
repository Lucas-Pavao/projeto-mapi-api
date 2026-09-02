package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TideSyncSummaryDTO;
import com.projeto.mapi.service.sensor.HistoricalDataService;
import com.projeto.mapi.service.tide.TideTableSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ingestion")
@RequiredArgsConstructor
@Tag(name = "Administração - Ingestão", description = "Endpoints para carregar dados históricos")
public class HistoricalDataController {

    private final com.projeto.mapi.service.sensor.HistoricalDataService historicalDataService;
    private final TideTableSyncService tideTableSyncService;

    @PostMapping("/historical-weather")
    @Operation(summary = "Inicia ingestão de histórico de chuva (Open-Meteo) para todos os pontos")
    public ResponseEntity<String> startMassiveIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalData(years);
        return ResponseEntity.ok("Ingestão de " + years + " anos (Clima) iniciada em segundo plano.");
    }

    @PostMapping("/historical-sensors")
    @Operation(summary = "Inicia ingestão de histórico de sensores (ANA) para todos os pontos")
    public ResponseEntity<String> startSensorIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalSensors(years);
        return ResponseEntity.ok("Ingestão de " + years + " anos (Sensores ANA) iniciada em segundo plano.");
    }

    @PostMapping("/historical-civil-defense")
    @Operation(summary = "Inicia ingestão de histórico da Defesa Civil (Recife) para os últimos N anos")
    public ResponseEntity<String> startCivilDefenseIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestCivilDefenseData(years);
        return ResponseEntity.ok("Ingestão de dados da Defesa Civil (últimos " + years + " anos) iniciada em segundo plano.");
    }

    @PostMapping("/historical-apac")
    @Operation(summary = "Inicia ingestão de histórico de chuva (APAC) para uma estação específica")
    public ResponseEntity<String> startApacIngestion(@RequestParam String stationCode, @RequestParam int year) {
        historicalDataService.ingestApacHistoricalRainfall(stationCode, year);
        return ResponseEntity.ok("Ingestão de dados da APAC para estação " + stationCode + " e ano " + year + " iniciada.");
    }

    @PostMapping("/historical-apac-full")
    @Operation(summary = "Inicia ingestão de histórico de chuva (APAC) para TODO o estado no ano especificado")
    public ResponseEntity<String> startFullApacIngestion(@RequestParam int year) {
        historicalDataService.ingestApacFullStateRainfall(year);
        return ResponseEntity.ok("Ingestão TOTAL da APAC (Estado de PE) para o ano " + year + " iniciada em segundo plano.");
    }

    @PostMapping("/historical-full-sync")
    @Operation(summary = "Executa sincronização TOTAL (Clima, ANA, APAC, Defesa Civil) de todos os pontos")
    public ResponseEntity<String> startFullSync(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalData(years);
        return ResponseEntity.ok("Sincronização TOTAL de " + years + " anos iniciada em segundo plano para todos os pontos.");
    }

    @PostMapping("/align-events")
    @Operation(summary = "Alinha eventos de alagamento (00:00) ao pico de chuva meteorológica do dia")
    public ResponseEntity<String> alignEvents() {
        historicalDataService.alignFloodEventsToRainPeaks();
        return ResponseEntity.ok("Processo de alinhamento de eventos iniciado.");
    }

    @GetMapping("/check-integrity")
    @Operation(summary = "Verifica a integridade dos dados no banco (Gaps e quantidades)")
    public ResponseEntity<java.util.List<com.projeto.mapi.dto.DataHealthReportDTO>> checkIntegrity() {
        return ResponseEntity.ok(historicalDataService.checkDataIntegrity());
    }

    @PostMapping("/repair-stations")
    @Operation(summary = "Repara o mapeamento de estações pluviométricas dos pontos piloto")
    public ResponseEntity<String> repairStations() {
        historicalDataService.repairStationMappings();
        return ResponseEntity.ok("Mapeamento de estações reparado com sucesso.");
    }

    @PostMapping("/tide-sync")
    @Operation(summary = "Sincroniza a tábua de maré local (TabuaMare/DHN) para os portos mais próximos de todos os pontos cadastrados")
    public ResponseEntity<TideSyncSummaryDTO> syncTideTables(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : java.time.Year.now().getValue();
        return ResponseEntity.ok(tideTableSyncService.syncYear(targetYear));
    }

    @DeleteMapping("/wipe-database")
    @Operation(summary = "LIMPA TODO O BANCO DE DADOS (Clima, Sensores e Eventos)")
    public ResponseEntity<String> wipeDatabase() {
        historicalDataService.wipeDatabase();
        return ResponseEntity.ok("Banco de dados resetado com sucesso (100% limpo).");
    }
}

