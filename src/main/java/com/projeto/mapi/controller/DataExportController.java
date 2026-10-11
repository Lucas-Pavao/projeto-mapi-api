package com.projeto.mapi.controller;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.service.export.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Exportação de Dados", description = "Endpoints para extração de datasets unificados para IA")
public class DataExportController {

    private final DataExportService dataExportService;

    @GetMapping("/ia-dataset/stream")
    @Operation(summary = "Exporta dataset unificado via Streaming JSON (Alta Performance)")
    public ResponseEntity<StreamingResponseBody> streamUnifiedData(
            @RequestParam(defaultValue = "30") int days) {
        
        StreamingResponseBody responseBody = outputStream -> {
            dataExportService.streamUnifiedDataToOutputStream(outputStream, days);
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=mapi_ia_dataset.json")
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    @GetMapping("/ia-dataset/{slug}")
    @Operation(summary = "Exporta dataset unificado (Sensores + Clima + Maré + Labels) para um ponto")
    public ResponseEntity<List<UnifiedDataDTO>> getUnifiedData(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(dataExportService.exportUnifiedData(slug, days));
    }

    @GetMapping("/ia-dataset/{slug}/csv")
    @Operation(summary = "Exporta dataset unificado em CSV; X-Dataset-Id = SHA-256 do CSV (ver /manifest)")
    public ResponseEntity<String> getUnifiedDataCsv(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) java.time.Instant to) {
        var export = dataExportService.exportCsv(slug, days, toUtc(to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataset_" + slug + ".csv")
                .header("X-Dataset-Id", export.manifest().datasetId())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(export.csv());
    }

    @GetMapping("/ia-dataset/{slug}/manifest")
    @Operation(summary = "Manifesto do dataset: hash do CSV, período, versões e run_ids por fonte/variável")
    public ResponseEntity<com.projeto.mapi.dto.ExportManifestDTO> getManifest(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days,
            @RequestParam(required = false) java.time.Instant to) {
        return ResponseEntity.ok(dataExportService.exportCsv(slug, days, toUtc(to)).manifest());
    }

    @GetMapping("/ia-dataset/all/csv")
    @Operation(summary = "Exporta dataset unificado de TODOS os pontos em formato CSV único")
    public ResponseEntity<String> getAllPointsDataCsv(
            @RequestParam(defaultValue = "0") int days,
            @RequestParam(required = false) java.time.Instant to) {
        var export = dataExportService.exportCsv("all", days, toUtc(to));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataset_full_history.csv")
                .header("X-Dataset-Id", export.manifest().datasetId())
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(export.csv());
    }

    private static java.time.LocalDateTime toUtc(java.time.Instant to) {
        return to == null ? null : java.time.LocalDateTime.ofInstant(to, java.time.ZoneOffset.UTC);
    }
}
