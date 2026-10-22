package com.projeto.mapi.controller;

import com.projeto.mapi.dto.AlertDTO;
import com.projeto.mapi.dto.AlertRuleDTO;
import com.projeto.mapi.dto.AlertRuleInputDTO;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.model.User;
import com.projeto.mapi.service.alert.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Alertas e regras de alerta (ADR 0011). Perfil mínimo: OPERADOR (ADR 0010). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Validated
@Tag(name = "Alertas", description = "Alertas disparados e regras de alerta")
public class AlertController {

    private final AlertService alertService;

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Lista alertas (OPERADOR)",
            description = "status OPEN (padrão), ACKNOWLEDGED ou ALL. from/to são opcionais (filtram triggeredAt).")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<PageResponseDTO<AlertDTO>> list(
            @RequestParam(defaultValue = "OPEN") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(alertService.list(status, from, to, page, size));
    }

    @PostMapping("/alerts/{id}/ack")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Reconhece um alerta (OPERADOR). Idempotente.")
    @ApiResponse(responseCode = "200", description = "Alerta reconhecido")
    @ApiResponse(responseCode = "404", description = "Alerta inexistente")
    public ResponseEntity<AlertDTO> acknowledge(@PathVariable Long id, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(alertService.acknowledge(id, user.getUsername()));
    }

    @GetMapping("/alert-rules")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Lista regras de alerta (OPERADOR)")
    @ApiResponse(responseCode = "200", description = "OK")
    public ResponseEntity<List<AlertRuleDTO>> listRules() {
        return ResponseEntity.ok(alertService.listRules());
    }

    @PostMapping("/alert-rules")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Cria regra de alerta (OPERADOR)")
    @ApiResponse(responseCode = "201", description = "Criada")
    @ApiResponse(responseCode = "400", description = "Regra inválida")
    public ResponseEntity<AlertRuleDTO> createRule(@Valid @RequestBody AlertRuleInputDTO input,
                                                   @AuthenticationPrincipal User user) {
        return ResponseEntity.status(HttpStatus.CREATED).body(alertService.createRule(input, user.getUsername()));
    }

    @PutMapping("/alert-rules/{id}")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Substitui regra de alerta (OPERADOR)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Regra inexistente")
    public ResponseEntity<AlertRuleDTO> updateRule(@PathVariable Long id, @Valid @RequestBody AlertRuleInputDTO input) {
        return ResponseEntity.ok(alertService.updateRule(id, input));
    }

    @DeleteMapping("/alert-rules/{id}")
    @PreAuthorize("hasAuthority('OPERADOR')")
    @Operation(summary = "Remove regra de alerta (OPERADOR). Alertas já disparados ficam.")
    @ApiResponse(responseCode = "204", description = "Removida")
    @ApiResponse(responseCode = "404", description = "Regra inexistente")
    public ResponseEntity<Void> deleteRule(@PathVariable Long id) {
        alertService.deleteRule(id);
        return ResponseEntity.noContent().build();
    }
}
