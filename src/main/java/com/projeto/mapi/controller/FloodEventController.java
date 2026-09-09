package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodEventDTO;
import com.projeto.mapi.dto.ScraperEventDTO;
import com.projeto.mapi.service.flood.FloodEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/eventos-alagamento")
@RequiredArgsConstructor
@Tag(name = "Eventos de Alagamento", description = "Endpoints para registro histórico de alagamentos (Labels para IA)")
public class FloodEventController {

    private final FloodEventService floodEventService;

    @PostMapping
    @Operation(summary = "Registra a ocorrência real de um alagamento (via slug)")
    public ResponseEntity<FloodEventDTO> reportFlood(@Valid @RequestBody FloodEventDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(floodEventService.reportFlood(dto));
    }

    @PostMapping("/ingest")
    @Operation(summary = "Ingere dados brutos de alagamento via scraper (usando coordenadas). Retorna 201 se um evento novo foi criado, 204 se já existia um evento equivalente (mesmo ponto e dia) e foi ignorado")
    public ResponseEntity<FloodEventDTO> ingestScraperEvent(@Valid @RequestBody ScraperEventDTO dto) {
        FloodEventDTO event = floodEventService.ingestScraperEvent(dto);
        if (event == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(event);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "Retorna o histórico de alagamentos de um ponto específico (paginado)")
    public ResponseEntity<Page<FloodEventDTO>> getHistory(
            @PathVariable String slug,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(floodEventService.getHistoryByPoint(slug, pageable));
    }
}
