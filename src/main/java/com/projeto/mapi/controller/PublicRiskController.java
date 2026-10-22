package com.projeto.mapi.controller;

import com.projeto.mapi.dto.PublicRiskDTO;
import com.projeto.mapi.service.publico.PublicRiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
@Tag(name = "Público", description = "Consulta sem login (ADR 0013)")
public class PublicRiskController {

    private final PublicRiskService publicRiskService;

    @GetMapping("/risk")
    @Operation(summary = "Risco perto de uma coordenada, sem login (ADR 0013)",
            description = "Sem probabilidade numérica; previsão experimental. Limite de 30 requisições por minuto por IP.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "422", description = "Coordenada fora da RMR")
    @ApiResponse(responseCode = "429", description = "Limite de taxa (Retry-After)")
    public ResponseEntity<PublicRiskDTO> risk(@RequestParam double lat, @RequestParam double lon) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic())
                .body(publicRiskService.assess(lat, lon));
    }
}
