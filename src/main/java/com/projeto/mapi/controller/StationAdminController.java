package com.projeto.mapi.controller;

import com.projeto.mapi.dto.MeasuredRainAccumulationsDTO;
import com.projeto.mapi.dto.StationUnificationReportDTO;
import com.projeto.mapi.service.sensor.cemaden.MeasuredRainAccumulationService;
import com.projeto.mapi.service.station.StationUnificationService;
import com.projeto.mapi.dto.StationMonthChecksDTO;
import com.projeto.mapi.service.analise.StationMonthChecksService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Unificação de estações (ADR 0023). Papel ADMIN (prefixo /api/admin). Síncrono. */
@RestController
@RequestMapping("/api/admin/stations")
@RequiredArgsConstructor
@Tag(name = "Administração - Estações", description = "Estação canônica e acumulados medidos (ADR 0023)")
public class StationAdminController {

    private final StationUnificationService unification;
    private final MeasuredRainAccumulationService accumulations;
    private final StationMonthChecksService monthChecks;

    @PostMapping("/unify")
    @Operation(summary = "Refaz a unificação das estações do Cemaden (ao vivo × PED) e a contagem de divergências",
            description = "Derivado: apaga e refaz station_canonical, station_alias e station_hour_divergence. Mesma "
                    + "estação = até 150 m; nome só vai para o registro. Com as duas fontes na mesma hora fica a PED.")
    @ApiResponse(responseCode = "200", description = "Tabela da unificação")
    @ApiResponse(responseCode = "403", description = "Sem perfil ADMIN")
    public ResponseEntity<StationUnificationReportDTO> unify() {
        return ResponseEntity.ok(unification.unify());
    }

    @PostMapping("/rain-accumulations/refresh")
    @Operation(summary = "Busca agora os acumulados medidos na PED (síncrono, ~6 s por município)",
            description = "Mesmo trabalho do ciclo agendado; respeita o limite de 10 requisições por minuto.")
    @ApiResponse(responseCode = "200", description = "Acumulados recém-lidos")
    @ApiResponse(responseCode = "403", description = "Sem perfil ADMIN")
    public ResponseEntity<MeasuredRainAccumulationsDTO> refreshAccumulations() {
        accumulations.refresh();
        return ResponseEntity.ok(accumulations.current());
    }

    @GetMapping("/month-checks")
    @Operation(summary = "Grade estação x mês da checagem espacial da PED (regra vigente)",
            description = "Meses com início em [from, to), no máximo 24; padrão: últimos 6 meses. Status OK | UNCHECKED | FAIL | "
                    + "OFFLINE; célula ausente = mês não checado. Instantes em UTC.")
    @ApiResponse(responseCode = "200", description = "Grade")
    @ApiResponse(responseCode = "400", description = "Janela inválida ou maior que 24 meses")
    @ApiResponse(responseCode = "403", description = "Sem perfil ADMIN")
    public ResponseEntity<StationMonthChecksDTO> monthChecks(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(monthChecks.grid(from, to));
    }
}
