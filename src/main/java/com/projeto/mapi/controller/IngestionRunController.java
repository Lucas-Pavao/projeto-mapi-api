package com.projeto.mapi.controller;

import com.projeto.mapi.dto.IngestionRunRequestDTO;
import com.projeto.mapi.exception.ResourceNotFoundException;
import com.projeto.mapi.service.ingestion.IngestionPipelineService;
import com.projeto.mapi.service.ingestion.IngestionRequest;
import com.projeto.mapi.service.ingestion.IngestionRunView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.access.prepost.PreAuthorize;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.service.ingestion.IngestionAdminService;

import java.util.Map;
import java.util.UUID;

/** Reprocessamento idempotente de [from, to) de uma fonte (ADR 0006 seção 3). Papel ADMIN (SecurityConfig). */
@RestController
@RequestMapping("/api/admin/ingestion/runs")
@RequiredArgsConstructor
@Validated
@Tag(name = "Administração - Pipeline de ingestão", description = "Execução e consulta de ingestion_run")
public class IngestionRunController {

    private final IngestionPipelineService pipeline;
    private final IngestionAdminService admin;

    @PostMapping
    @Operation(summary = "Dispara REFETCH ou REVALIDATE de [from, to) de uma fonte (assíncrono)")
    @ApiResponse(responseCode = "202", description = "Run aberto; consulte GET /runs/{id}")
    public ResponseEntity<Map<String, UUID>> start(@Valid @RequestBody IngestionRunRequestDTO body) {
        UUID id = pipeline.submit(new IngestionRequest(body.source(), body.from(), body.to(), body.mode(), body.kind()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("runId", id));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lista execuções de ingestão, paginada (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK (itens no mesmo formato de GET /runs/{id})")
    public ResponseEntity<PageResponseDTO<IngestionRunView>> list(
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(admin.listRuns(source, status, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Consulta um ingestion_run (status, contagens, versões)")
    public ResponseEntity<IngestionRunView> get(@PathVariable UUID id) {
        return ResponseEntity.ok(pipeline.findRun(id)
                .orElseThrow(() -> new ResourceNotFoundException("ingestion_run não encontrado: " + id)));
    }
}
