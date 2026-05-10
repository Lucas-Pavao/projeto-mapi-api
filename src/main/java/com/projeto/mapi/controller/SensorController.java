package com.projeto.mapi.controller;

import com.projeto.mapi.model.SensorData;
import com.projeto.mapi.service.SensorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    public ResponseEntity<List<SensorData>> getAllLatest() {
        return ResponseEntity.ok(sensorService.getAllLatestData());
    }

    @GetMapping("/{sensorId}/history")
    @Operation(summary = "Ver o histórico de leituras de um sensor específico")
    public ResponseEntity<List<SensorData>> getSensorHistory(@PathVariable String sensorId) {
        return ResponseEntity.ok(sensorService.getSensorHistory(sensorId));
    }
}
