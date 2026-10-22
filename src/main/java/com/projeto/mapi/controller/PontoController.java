package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodPointPatchDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.PredictionSeriesDTO;
import com.projeto.mapi.dto.RiskSummaryDTO;
import com.projeto.mapi.dto.StationSuggestionDTO;
import com.projeto.mapi.service.flood.FloodPointAdminService;
import com.projeto.mapi.service.flood.PredictionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

/** Endpoints novos de pontos do redesign (docs/openapi/redesign-telas.yaml). */
@RestController
@RequestMapping("/api/pontos")
@RequiredArgsConstructor
@Validated
@Tag(name = "Pontos", description = "Pontos críticos: edição, sugestão de estações, resumo e série de risco")
public class PontoController {

    private final FloodPointAdminService adminService;
    private final PredictionQueryService predictionQueryService;

    @GetMapping("/risk-summary")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Contagem de pontos ativos por nível de risco (última previsão de cada ponto)")
    @ApiResponse(responseCode = "200", description = "OK")
    public ResponseEntity<RiskSummaryDTO> riskSummary() {
        return ResponseEntity.ok(predictionQueryService.riskSummary());
    }

    @GetMapping("/station-suggestions")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Estações a até radiusM de uma coordenada, para o cadastro de pontos (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Estações ordenadas por distância")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<List<StationSuggestionDTO>> stationSuggestions(
            @RequestParam @DecimalMin("-8.4") @DecimalMax("-7.8") double lat,
            @RequestParam @DecimalMin("-35.2") @DecimalMax("-34.8") double lon,
            @RequestParam(defaultValue = "3000") @Min(100) @Max(10000) int radiusM) {
        return ResponseEntity.ok(adminService.stationSuggestions(lat, lon, radiusM));
    }

    @PatchMapping("/{slug}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Edita ou ativa/desativa um ponto crítico (ADMIN)")
    @ApiResponse(responseCode = "200", description = "Ponto atualizado (mesmo corpo de GET /api/pontos/{slug})")
    @ApiResponse(responseCode = "400", description = "Nenhum campo ou valor inválido")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    @ApiResponse(responseCode = "404", description = "Ponto inexistente")
    public ResponseEntity<FloodPointResponseDTO> patch(@PathVariable String slug,
                                                       @Valid @RequestBody FloodPointPatchDTO patch) {
        return ResponseEntity.ok(adminService.patch(slug, patch));
    }

    @GetMapping("/{slug}/predictions")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Série de previsões do ponto (de flood_predictions), sem gravar nada",
            description = "Ordenada por issuedAt. Janela máxima de 7 dias.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Janela inválida ou maior que 7 dias")
    @ApiResponse(responseCode = "404", description = "Ponto inexistente")
    public ResponseEntity<PredictionSeriesDTO> predictions(
            @PathVariable String slug,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ResponseEntity.ok(predictionQueryService.series(slug, from, to));
    }
}
