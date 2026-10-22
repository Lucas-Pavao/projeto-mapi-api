package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodReportDTO;
import com.projeto.mapi.dto.FloodReportInputDTO;
import com.projeto.mapi.dto.FloodReportReviewDTO;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.model.User;
import com.projeto.mapi.service.report.FloodReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
@Tag(name = "Relatos", description = "Relatos de alagamento enviados por usuários e revisados por operadores")
public class ReportController {

    private final FloodReportService reportService;

    @PostMapping
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Envia um relato de alagamento (USER). Nasce PENDING.")
    @ApiResponse(responseCode = "201", description = "Criado como PENDING")
    @ApiResponse(responseCode = "400", description = "Corpo inválido")
    public ResponseEntity<FloodReportDTO> submit(@Valid @RequestBody FloodReportInputDTO input,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.submit(input, user.getUsername()));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Fila de relatos (OPERADOR)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<PageResponseDTO<FloodReportDTO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(reportService.list(status, page, size));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Confirma ou descarta um relato (OPERADOR)",
            description = "CONFIRMED cria um FloodEvent ligado ao ponto informado. Não escreve em flood_occurrence.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Relato ou ponto inexistente")
    @ApiResponse(responseCode = "409", description = "Relato já revisado")
    public ResponseEntity<FloodReportDTO> review(@PathVariable Long id,
                                                 @Valid @RequestBody FloodReportReviewDTO review,
                                                 @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reportService.review(id, review, user.getUsername()));
    }
}
