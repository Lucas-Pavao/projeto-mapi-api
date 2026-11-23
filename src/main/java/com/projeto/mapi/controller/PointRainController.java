package com.projeto.mapi.controller;

import com.projeto.mapi.dto.PointRainCandidatesDTO;
import com.projeto.mapi.service.analise.PointRainCandidatesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Chuva do ponto: de onde veio o valor da hora (ADR 0022). DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC. */
@RestController
@RequestMapping("/api/pontos")
@RequiredArgsConstructor
@Tag(name = "Chuva do ponto", description = "Estações candidatas e valor da chuva do ponto crítico (ADR 0022)")
public class PointRainController {

    private final PointRainCandidatesService candidates;
    private final com.projeto.mapi.repository.analise.PointRainRepository repo;
    private final java.time.Clock clock;

    @GetMapping("/{slug}/rain-candidates")
    @Operation(summary = "Estações candidatas à chuva do ponto numa hora, com o papel de cada uma",
            description = "Estações vinculadas até 5 km, por distância. role: USED (maior valor entre as válidas), LOWER (válida, "
                    + "valor menor), EXCLUDED (mês FAIL ou OFFLINE; motivo em reasons), NO_VALUE (válida sem valor na hora). "
                    + "Válida = mês OK ou UNCHECKED. Sem 'at', usa a última hora com série; ponto sem série responde 200 com "
                    + "hourEndUtc nulo e lista vazia. Instantes em UTC.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "'at' não é hora cheia")
    @ApiResponse(responseCode = "404", description = "Ponto não encontrado")
    public ResponseEntity<PointRainCandidatesDTO> rainCandidates(@PathVariable String slug,
            @Parameter(description = "Fim da hora, ISO-8601 UTC, hora cheia") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ResponseEntity.ok(candidates.candidates(slug, at));
    }

    @GetMapping("/{slug}/chuva-medida")
    @Operation(summary = "Chuva medida acumulada no ponto: 1, 3, 6 e 24 h na última hora fechada (onda 16, Fase 7.3)",
            description = "Hora do ponto = maior valor entre as estações válidas a até 5 km (ADR 0022, chuva-ponto-v4). "
                    + "Acumulado com qualquer hora sem valor é nulo, nunca zero; n3h/n6h/n24h = horas com valor. "
                    + "Ponto sem série responde 404. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Ponto sem série medida")
    public ResponseEntity<com.projeto.mapi.dto.PointMeasuredRainDTO> measured(@PathVariable String slug) {
        return repo.latestAccum(slug, com.projeto.mapi.service.analise.PointRainService.RULE_VERSION, clock.instant())
                .map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
