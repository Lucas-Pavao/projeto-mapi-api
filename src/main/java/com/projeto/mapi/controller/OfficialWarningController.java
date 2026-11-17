package com.projeto.mapi.controller;

import com.projeto.mapi.dto.OfficialWarningsResponseDTO;
import com.projeto.mapi.service.warning.OfficialWarningService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Avisos meteorológicos oficiais vigentes (INMET, ADR 0024). */
@RestController
@RequestMapping("/api/warnings/official")
@RequiredArgsConstructor
@Tag(name = "Avisos Oficiais", description = "Avisos meteorológicos severos oficiais (INMET, ADR 0024)")
public class OfficialWarningController {

    private final OfficialWarningService service;

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Avisos meteorológicos oficiais vigentes do INMET",
            description = "Leitura pura dos avisos persistidos pela coleta periódica. "
                    + "firstSeenAt é proxy posterior à emissão real (a API pública do INMET não publica hora de emissão).")
    @ApiResponse(responseCode = "200", description = "OK (lista vazia quando não há aviso vigente para a região)")
    public ResponseEntity<OfficialWarningsResponseDTO> active(
            @Parameter(description = "Opcional: código IBGE de 7 dígitos (ex.: 2611606) ou nome do município (ex.: Recife)")
            @RequestParam(required = false) String municipio) {
        return ResponseEntity.ok(service.activeWarnings(municipio));
    }
}
