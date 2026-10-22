package com.projeto.mapi.controller;

import com.projeto.mapi.dto.IngestionSourceStatusDTO;
import com.projeto.mapi.service.ingestion.IngestionAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/ingestion/sources")
@RequiredArgsConstructor
@Tag(name = "Administração - Pipeline de ingestão", description = "Execução e consulta de ingestion_run")
public class IngestionSourcesController {

    private final IngestionAdminService admin;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Status por fonte de ingestão (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<List<IngestionSourceStatusDTO>> sources() {
        return ResponseEntity.ok(admin.sources());
    }
}
