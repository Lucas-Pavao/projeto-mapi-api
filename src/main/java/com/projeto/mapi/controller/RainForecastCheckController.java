package com.projeto.mapi.controller;

import com.projeto.mapi.service.flood.RainForecastCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;

/** Conferência semanal do rain_v1 contra a persistência (onda 16, Fase 1.1). Papel ADMIN. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Previsão de chuva", description = "Conferência semanal do rain_v1, antes e depois de cada quebra de série")
public class RainForecastCheckController {

    private final RainForecastCheckService service;

    @GetMapping("/api/rain-forecast/weekly-check")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Últimas linhas da conferência semanal (semana e acumulado de cada trecho)")
    public List<Map<String, Object>> latest(@RequestParam(defaultValue = "30") int limit) {
        return service.latest(Math.max(1, Math.min(limit, 500)));
    }

    @PostMapping("/api/admin/rain-forecast/weekly-check")
    @Operation(summary = "Roda a conferência da semana que contém a data (segunda a segunda, hora de Recife)")
    @ApiResponse(responseCode = "200", description = "linhas gravadas")
    public ResponseEntity<Map<String, Object>> run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        LocalDate monday = semana.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ResponseEntity.ok(Map.of("semana", monday.toString(), "linhas", service.runWeek(monday)));
    }
}
