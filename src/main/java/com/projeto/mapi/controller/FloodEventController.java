package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import com.projeto.mapi.service.FloodEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/eventos-alagamento")
@RequiredArgsConstructor
@Tag(name = "Eventos de Alagamento", description = "Endpoints para registro histórico de alagamentos (Labels para IA)")
public class FloodEventController {

    private final FloodEventService floodEventService;

    @PostMapping
    @Operation(summary = "Registra a ocorrência real de um alagamento (via slug)")
    public ResponseEntity<FloodEventDTO> reportFlood(@RequestBody FloodEventDTO dto) {
        return ResponseEntity.ok(floodEventService.reportFlood(dto));
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ingere dados brutos de alagamento via scraper (usando coordenadas)")
    public ResponseEntity<FloodEventDTO> ingestScraperEvent(@RequestBody ScraperEventDTO dto) {
        return ResponseEntity.ok(floodEventService.ingestScraperEvent(dto));
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Retorna o histórico de alagamentos de um ponto específico")
    public ResponseEntity<List<FloodEventDTO>> getHistory(@PathVariable String slug) {
        return ResponseEntity.ok(floodEventService.getHistoryByPoint(slug));
    }
}
