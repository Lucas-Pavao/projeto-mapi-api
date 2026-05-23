package com.projeto.mapi.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.projeto.mapi.service.MarineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/marine")
@RequiredArgsConstructor
@Tag(name = "Marine Data (Open-Meteo)", description = "Integração com a API Marine da Open-Meteo")
public class MarineController {

    private final MarineService marineService;

    @GetMapping
    @Operation(summary = "Obter dados marinhos (ondas, etc) por latitude e longitude")
    public ResponseEntity<JsonNode> getMarineData(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(marineService.getMarineData(latitude, longitude));
    }

    @GetMapping("/wave-height")
    @Operation(summary = "Obter altura da onda atual")
    public ResponseEntity<Double> getWaveHeight(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(marineService.getCurrentWaveHeight(latitude, longitude));
    }
}
