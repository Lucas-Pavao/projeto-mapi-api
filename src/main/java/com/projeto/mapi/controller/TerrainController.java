package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TerrainResponseDTO;
import com.projeto.mapi.service.terrain.TerrainQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pontos")
@RequiredArgsConstructor
@Tag(name = "Terreno (PE3D)", description = "Atributos do terreno por ponto crítico (ADR 0008)")
public class TerrainController {

    private final TerrainQueryService terrainService;

    @GetMapping("/{slug}/terrain")
    @Operation(summary = "Terreno do PE3D do ponto (execução vigente)",
            description = "Cotas em metros no datum do MDT (não confirmado), NÃO comparáveis ao nível da maré (ADR 0009). "
                    + "Ponto sem terreno responde 200 com status NO_DATA e atributos nulos.")
    @ApiResponse(responseCode = "200", description = "Terreno do ponto, ou status NO_DATA")
    @ApiResponse(responseCode = "404", description = "Ponto não encontrado")
    public ResponseEntity<TerrainResponseDTO> getTerrain(@PathVariable String slug) {
        return ResponseEntity.ok(terrainService.findBySlug(slug));
    }
}
