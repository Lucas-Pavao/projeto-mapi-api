package com.projeto.mapi.controller;

import com.projeto.mapi.dto.ModelEvaluationDTO;
import com.projeto.mapi.service.desempenho.BucketSimulationService;
import com.projeto.mapi.service.desempenho.ModelEvaluationService;
import com.projeto.mapi.dto.BucketSimulationDTO;
import com.projeto.mapi.dto.BucketSimulationRequestDTO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/model")
@RequiredArgsConstructor
@Tag(name = "Modelo", description = "Desempenho congelado e simulação do balde")
public class ModelController {

    private final ModelEvaluationService evaluationService;
    private final BucketSimulationService simulationService;

    @GetMapping("/evaluation")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Resultado congelado da avaliação F1 (resultado_etapa2.json), sem recálculo",
            description = "Servido como está. O SHA-256 do arquivo é conferido a cada leitura; se não bater com o esperado, 503.")
    @ApiResponse(responseCode = "200", description = "OK; cabeçalho X-Content-SHA256 com o hash conferido")
    @ApiResponse(responseCode = "503", description = "Arquivo ausente ou hash divergente")
    public ResponseEntity<ModelEvaluationDTO> evaluation() {
        ModelEvaluationDTO dto = evaluationService.read();
        return ResponseEntity.ok().header("X-Content-SHA256", dto.sha256()).body(dto);
    }

    @PostMapping("/simulate/bucket")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Simula o balde M1a num ponto (repassa para a IA, ADR 0014). Não é previsão.",
            description = "Sem controle de maré e sem escrita no banco. tCrossH é o índice 0-based em rainfallMmH.")
    @ApiResponse(responseCode = "200", description = "OK (illustrative = true)")
    @ApiResponse(responseCode = "400", description = "Corpo inválido")
    @ApiResponse(responseCode = "404", description = "Ponto inexistente")
    @ApiResponse(responseCode = "422", description = "Ponto sem terreno (ex. Jaboatão) ou parâmetro fora da faixa")
    @ApiResponse(responseCode = "503", description = "IA indisponível")
    public ResponseEntity<BucketSimulationDTO> simulateBucket(@Valid @RequestBody BucketSimulationRequestDTO request) {
        return ResponseEntity.ok(simulationService.simulate(request));
    }
}
