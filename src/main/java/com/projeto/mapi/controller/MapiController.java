package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import com.projeto.mapi.service.MapiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "MAPI", description = "Endpoints integrados do Projeto MAPI")
public class MapiController {

    private final MapiService mapiService;

    @GetMapping("/precise-data")
    @Operation(summary = "Busca dados ambientais precisos comparando sensores locais e Open-Meteo")
    public ResponseEntity<MapiResponseDTO> getPreciseData(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(mapiService.getPreciseData(latitude, longitude));
    }

    @PostMapping("/pontos")
    @Operation(summary = "Registra um novo ponto de monitoramento de alagamento")
    public ResponseEntity<FloodPointResponseDTO> createFloodPoint(
            @Valid @RequestBody FloodPointRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapiService.createFloodPoint(request));
    }

    @GetMapping("/pontos")
    @Operation(summary = "Lista todos os pontos de monitoramento registrados")
    public ResponseEntity<List<FloodPointResponseDTO>> getAllFloodPoints() {
        return ResponseEntity.ok(mapiService.getAllFloodPoints());
    }

    @GetMapping("/pontos/{id_ponto}")
    @Operation(summary = "Busca o status atual de um ponto específico")
    public ResponseEntity<MapiResponseDTO> getPointStatus(@PathVariable String id_ponto) {
        FloodPointResponseDTO point = mapiService.getFloodPointBySlug(id_ponto);
        if (point == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(mapiService.getPreciseData(point.getLatitude(), point.getLongitude()));
    }
}
