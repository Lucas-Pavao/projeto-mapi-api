package com.projeto.mapi.controller;

import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.service.sensor.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sensors")
@RequiredArgsConstructor
@Tag(name = "Sensors", description = "Endpoints para monitoramento de sensores IoT")
public class SensorController {

    private final SensorService sensorService;
    private final com.projeto.mapi.service.sensor.cemaden.CemadenAccumulationService cemadenAccumulation;

    @GetMapping("/cemaden/accumulated")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Acumulados oficiais de chuva da CEMADEN na RMR (1 a 96 h), com a conferência do MAPi",
            description = "Painel público da CEMADEN, reaproveitado por 5 min. Acumulado nulo = a estação não transmitiu "
                    + "na janela. mapiAcc24hMm é a soma das horas gravadas pelo MAPi (blocos de 10/60 min) e diff24hMm a "
                    + "diferença para o acc24hMm oficial; stationId/lat/lon vêm da estação casada pelo nome.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "503", description = "Painel da CEMADEN indisponível")
    public ResponseEntity<List<com.projeto.mapi.dto.CemadenAccumulationDTO>> cemadenAccumulated() {
        return ResponseEntity.ok(cemadenAccumulation.rmr());
    }

    @GetMapping("/latest")
    @Operation(summary = "Ver as leituras recentes de todos os sensores, com frescor",
            description = "Campos antigos mantidos; acrescenta observedAt (UTC), ageSeconds e stale (idade > 2x o intervalo esperado da fonte). "
                    + "O filtro type aceita PRECIPITATION ou RIVER_LEVEL.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "type fora de PRECIPITATION/RIVER_LEVEL")
    public ResponseEntity<List<SensorResponseDTO>> getAllLatest(@RequestParam(required = false) String type) {
        if (type != null && !type.equals("PRECIPITATION") && !type.equals("RIVER_LEVEL")) {
            throw new com.projeto.mapi.exception.InvalidRequestException("type deve ser PRECIPITATION ou RIVER_LEVEL");
        }
        // Leitura sem chuva nem nível (ex.: ANA com Chuva_Adotada e Cota_Adotada nulas) não tem tipo: fica de fora
        // do contrato, que só aceita PRECIPITATION ou RIVER_LEVEL.
        return ResponseEntity.ok(sensorService.getLatestWithFreshness(type).stream()
                .filter(s -> "PRECIPITATION".equals(s.getType()) || "RIVER_LEVEL".equals(s.getType()))
                .toList());
    }

    @GetMapping("/{sensorId}/latest")
    @Operation(summary = "Ver a leitura mais recente de um sensor específico")
    public ResponseEntity<SensorResponseDTO> getLatestBySensorId(@PathVariable String sensorId) {
        SensorResponseDTO sensor = sensorService.getLatestBySensorId(sensorId);
        return sensor != null ? ResponseEntity.ok(sensor) : ResponseEntity.notFound().build();
    }

    @GetMapping("/{sensorId}/history")
    @Operation(summary = "Ver o histórico de leituras de um sensor específico (paginado)")
    public ResponseEntity<Page<SensorResponseDTO>> getSensorHistory(
            @PathVariable String sensorId,
            @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(sensorService.getSensorHistory(sensorId, pageable));
    }

    @GetMapping("/ids")
    @Operation(summary = "Listar todos os IDs de sensores únicos cadastrados (APAC/ANA)")
    public ResponseEntity<List<String>> getDistinctSensorIds() {
        return ResponseEntity.ok(sensorService.getDistinctSensorIds());
    }

    @GetMapping("/inventory")
    @Operation(summary = "Listar todos os sensores históricos cadastrados com metadados (paginado)")
    public ResponseEntity<Page<SensorResponseDTO>> getFullSensorInventory(@PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(sensorService.getFullSensorInventory(pageable));
    }
}
