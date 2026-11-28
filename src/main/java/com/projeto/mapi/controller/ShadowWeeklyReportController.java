package com.projeto.mapi.controller;

import com.projeto.mapi.service.alagamento.ShadowWeeklyReportService;
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

/** Relatório semanal do modo sombra (onda 16, Fase 9.4; V49). Papel ADMIN. Sem rótulo de alagamento: rótulos lacrados. */
@RestController
@RequiredArgsConstructor
@Tag(name = "Alerta de alagamento por regressão", description = "Relatório semanal do modo sombra")
public class ShadowWeeklyReportController {

    private final ShadowWeeklyReportService service;

    @GetMapping("/api/alagamento/relatorio-semanal")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Últimas semanas do relatório do modo sombra, por ponto",
            description = "Ciclos feitos contra esperados (um a cada 10 min), maior buraco sem ciclo, buracos acima de 30 min, "
                    + "ciclos por nível, SEM_DADO por motivo e episódios de ALERTA e de ALERTA ou ATENÇÃO (separados por 3 h). "
                    + "labels_status = LACRADOS até o fim do período de validação.")
    public List<Map<String, Object>> latest(@RequestParam(defaultValue = "8") int semanas) {
        return service.latest(Math.max(1, Math.min(semanas, 104)));
    }

    @PostMapping("/api/admin/alagamento/relatorio-semanal")
    @Operation(summary = "Calcula a semana que contém a data (segunda a segunda, hora de Recife); semana em curso vai até agora")
    @ApiResponse(responseCode = "200", description = "linhas gravadas")
    public ResponseEntity<Map<String, Object>> run(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate semana) {
        LocalDate monday = semana.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return ResponseEntity.ok(Map.of("semana", monday.toString(), "linhas", service.runWeek(monday)));
    }
}
