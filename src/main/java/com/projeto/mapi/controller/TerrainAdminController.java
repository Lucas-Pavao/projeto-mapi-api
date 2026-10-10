package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TerrainLoadResultDTO;
import com.projeto.mapi.service.terrain.TerrainLoadService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Carga do terreno do PE3D (ADR 0008). Papel ADMIN (SecurityConfig). O diretório vem de {@code mapi.terrain.dir}. */
@RestController
@RequestMapping("/api/admin/terrain/runs")
@RequiredArgsConstructor
@Tag(name = "Administração - Terreno", description = "Carga idempotente de ponto_terreno.csv em terrain_run")
public class TerrainAdminController {

    private final TerrainLoadService loadService;

    @PostMapping
    @Operation(summary = "Carrega o terreno do diretório configurado (mapi.terrain.dir)",
            description = "Idempotente: o mesmo conteúdo (SHA-256 dos 3 arquivos) devolve o terrain_run existente; "
                    + "conteúdo novo cria um terrain_run e o torna o vigente.")
    @ApiResponse(responseCode = "201", description = "Novo terrain_run criado e vigente")
    @ApiResponse(responseCode = "200", description = "Conteúdo já carregado; nada alterado")
    @ApiResponse(responseCode = "400", description = "Arquivo ausente ou inválido")
    public ResponseEntity<TerrainLoadResultDTO> load() {
        TerrainLoadResultDTO result = loadService.loadConfigured();
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK).body(result);
    }
}
