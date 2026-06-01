package com.projeto.mapi.controller;

import com.projeto.mapi.dto.UnifiedDataDTO;
import com.projeto.mapi.service.DataExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
@Tag(name = "Exportação de Dados", description = "Endpoints para extração de datasets unificados para IA")
public class DataExportController {

    private final DataExportService dataExportService;

    @GetMapping("/ia-dataset/{slug}")
    @Operation(summary = "Exporta dataset unificado (Sensores + Clima + Maré + Labels) para um ponto")
    public ResponseEntity<List<UnifiedDataDTO>> getUnifiedData(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days) {
        return ResponseEntity.ok(dataExportService.exportUnifiedData(slug, days));
    }

    @GetMapping("/ia-dataset/{slug}/csv")
    @Operation(summary = "Exporta dataset unificado em formato CSV")
    public ResponseEntity<String> getUnifiedDataCsv(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int days) {
        
        List<UnifiedDataDTO> data = dataExportService.exportUnifiedDataWithAccumulated(slug, days);
        String csv = dataExportService.generateCsv(data);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataset_" + slug + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/ia-dataset/all/csv")
    @Operation(summary = "Exporta dataset unificado de TODOS os pontos em formato CSV único")
    public ResponseEntity<String> getAllPointsDataCsv(
            @RequestParam(defaultValue = "0") int days) {
        
        // Otimização: Aplicar acumulados para todos os pontos
        List<com.projeto.mapi.model.FloodPoint> points = ((com.projeto.mapi.service.impl.DataExportServiceImpl)dataExportService).getPoints();
        List<UnifiedDataDTO> allData = new java.util.ArrayList<>();
        for (com.projeto.mapi.model.FloodPoint p : points) {
            allData.addAll(dataExportService.exportUnifiedDataWithAccumulated(p.getSlug(), days));
        }
        
        String csv = dataExportService.generateCsv(allData);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=dataset_full_history.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }
}
