package com.projeto.mapi.controller;

import com.projeto.mapi.dto.SensorResponseDTO;
import com.projeto.mapi.service.sensor.SensorService;
import io.swagger.v3.oas.annotations.Operation;
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

    @GetMapping("/latest")
    @Operation(summary = "Ver todas as leituras recentes de todos os sensores")
    public ResponseEntity<List<SensorResponseDTO>> getAllLatest() {
        return ResponseEntity.ok(sensorService.getAllLatestData());
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
