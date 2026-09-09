package com.projeto.mapi.controller;

import com.projeto.mapi.dto.IngestionAcceptedDTO;
import com.projeto.mapi.dto.TideSyncSummaryDTO;
import com.projeto.mapi.service.sensor.HistoricalDataService;
import com.projeto.mapi.service.tide.TideTableSyncService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ingestion")
@RequiredArgsConstructor
@Tag(name = "Administração - Ingestão", description = "Endpoints para carregar dados históricos")
public class HistoricalDataController {

    private final com.projeto.mapi.service.sensor.HistoricalDataService historicalDataService;
    private final TideTableSyncService tideTableSyncService;

    // Todos os endpoints de ingestão disparam um job @Async e retornam na hora — o trabalho real
    // ainda não terminou quando a resposta chega. 202 Accepted é o status HTTP correto pra isso
    // ("aceito, processando"), não 200 OK (que implica "já terminei"). Antes todos devolviam
    // 200 + uma String escrita à mão; agora é um DTO estruturado e consistente com o resto da API.
    private ResponseEntity<IngestionAcceptedDTO> accepted(String message) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new IngestionAcceptedDTO(message));
    }

    @PostMapping("/historical-weather")
    @Operation(summary = "Inicia ingestão de histórico de clima (Open-Meteo) para todos os pontos")
    public ResponseEntity<IngestionAcceptedDTO> startWeatherIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalWeather(years);
        return accepted("Ingestão de " + years + " anos (Clima) iniciada em segundo plano.");
    }

    @PostMapping("/historical-sensors")
    @Operation(summary = "Inicia ingestão de histórico de sensores (ANA + APAC) para todos os pontos")
    public ResponseEntity<IngestionAcceptedDTO> startSensorIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalSensors(years);
        return accepted("Ingestão de " + years + " anos (Sensores ANA + APAC) iniciada em segundo plano.");
    }

    @PostMapping("/historical-civil-defense")
    @Operation(summary = "Inicia ingestão de histórico da Defesa Civil (Recife) para os últimos N anos")
    public ResponseEntity<IngestionAcceptedDTO> startCivilDefenseIngestion(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestCivilDefenseData(years);
        return accepted("Ingestão de dados da Defesa Civil (últimos " + years + " anos) iniciada em segundo plano.");
    }

    @PostMapping("/historical-apac")
    @Operation(summary = "Inicia ingestão de histórico de chuva (APAC) para uma estação específica")
    public ResponseEntity<IngestionAcceptedDTO> startApacIngestion(@RequestParam String stationCode, @RequestParam int year) {
        historicalDataService.ingestApacHistoricalRainfall(stationCode, year);
        return accepted("Ingestão de dados da APAC para estação " + stationCode + " e ano " + year + " iniciada em segundo plano.");
    }

    @PostMapping("/historical-apac-full")
    @Operation(summary = "Inicia ingestão de histórico de chuva (APAC) para TODO o estado no ano especificado")
    public ResponseEntity<IngestionAcceptedDTO> startFullApacIngestion(@RequestParam int year) {
        historicalDataService.ingestApacFullStateRainfall(year);
        return accepted("Ingestão TOTAL da APAC (Estado de PE) para o ano " + year + " iniciada em segundo plano.");
    }

    @PostMapping("/historical-full-sync")
    @Operation(summary = "Executa sincronização TOTAL (Clima, ANA, APAC, Defesa Civil e alinhamento de eventos) de todos os pontos")
    public ResponseEntity<IngestionAcceptedDTO> startFullSync(@RequestParam(defaultValue = "5") int years) {
        historicalDataService.ingestHistoricalData(years);
        return accepted("Sincronização TOTAL de " + years + " anos iniciada em segundo plano para todos os pontos.");
    }

    @PostMapping("/align-events")
    @Operation(summary = "Alinha eventos de alagamento (00:00) ao pico de chuva meteorológica do dia (síncrono)")
    public ResponseEntity<IngestionAcceptedDTO> alignEvents() {
        historicalDataService.alignFloodEventsToRainPeaks();
        return ResponseEntity.ok(new IngestionAcceptedDTO("Alinhamento de eventos concluído."));
    }

    @GetMapping("/check-integrity")
    @Operation(summary = "Verifica a integridade dos dados no banco (Gaps e quantidades)")
    public ResponseEntity<java.util.List<com.projeto.mapi.dto.DataHealthReportDTO>> checkIntegrity() {
        return ResponseEntity.ok(historicalDataService.checkDataIntegrity());
    }

    @PostMapping("/repair-stations")
    @Operation(summary = "Repara o mapeamento de estações pluviométricas dos pontos piloto (síncrono)")
    public ResponseEntity<IngestionAcceptedDTO> repairStations() {
        historicalDataService.repairStationMappings();
        return ResponseEntity.ok(new IngestionAcceptedDTO("Mapeamento de estações reparado com sucesso."));
    }

    @PostMapping("/tide-sync")
    @Operation(summary = "Sincroniza a tábua de maré local (TabuaMare) para os portos mais próximos de todos os pontos cadastrados")
    public ResponseEntity<TideSyncSummaryDTO> syncTideTables(@RequestParam(required = false) Integer year) {
        int targetYear = year != null ? year : java.time.Year.now().getValue();
        return ResponseEntity.ok(tideTableSyncService.syncYear(targetYear));
    }

    @DeleteMapping("/wipe-database")
    @Operation(summary = "LIMPA TODO O BANCO DE DADOS (Clima, Sensores e Eventos)")
    public ResponseEntity<Void> wipeDatabase() {
        historicalDataService.wipeDatabase();
        return ResponseEntity.noContent().build();
    }
}

