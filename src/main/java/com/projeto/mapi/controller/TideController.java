package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.service.TideService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tide")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Tide", description = "Endpoints para consulta de tábua de maré (Multi-fonte)")
public class TideController {
    private final TideService tideService;

    @GetMapping("/{harbor}")
    @Operation(summary = "Obter tábua de maré para um porto específico")
    public ResponseEntity<TideTableResponseDTO> getTideTable(
            @PathVariable String harbor,
            @RequestParam(required = false) Integer year) {
        
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Buscando tábua de maré para o porto: {} e ano: {}", harbor, queryYear);

        Optional<TideTableResponseDTO> tideTable = tideService.getTideTable(harbor, queryYear);
        
        return tideTable.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/state/{state}")
    @Operation(summary = "Listar tábuas de maré por estado")
    public ResponseEntity<List<TideTableResponseDTO>> getTideByState(
            @PathVariable String state,
            @RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Buscando tábuas de maré para o estado: {} e ano: {}", state, queryYear);
        List<TideTableResponseDTO> results = tideService.getTideTablesByState(state, queryYear);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search")
    @Operation(summary = "Pesquisar portos por nome")
    public ResponseEntity<List<TideTableResponseDTO>> searchTide(
            @RequestParam String harbor,
            @RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Pesquisando portos por nome: {} e ano: {}", harbor, queryYear);
        List<TideTableResponseDTO> results = tideService.searchTideTablesByHarbor(harbor, queryYear);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/harbors")
    @Operation(summary = "Listar todos os portos disponíveis")
    public ResponseEntity<List<String>> listHarbors(@RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Listando todos os portos disponíveis para o ano: {}", queryYear);
        List<String> results = tideService.getAllHarbors(queryYear);
        return ResponseEntity.ok(results);
    }
}
