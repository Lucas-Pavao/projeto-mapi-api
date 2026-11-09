package com.projeto.mapi.controller;

import com.projeto.mapi.dto.LabelInventoryDTO;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceClusterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Rótulos multifonte (ADR 0019). Papel ADMIN (prefixo /api/admin). Síncrono. */
@RestController
@RequestMapping("/api/admin/ingestion/occurrences")
@RequiredArgsConstructor
@Tag(name = "Administração - Rótulos", description = "Deduplicação entre fontes e inventário de rótulos (ADR 0019)")
public class LabelAdminController {

    private final OccurrenceClusterService service;

    @PostMapping("/clusters")
    @Operation(summary = "Recalcula os grupos de deduplicação entre fontes e devolve o inventário de rótulos",
            description = "Mesmo tipo, mesmo dia local (America/Recife) e até 200 m = um alagamento. Os grupos são "
                    + "derivados: apagados e refeitos a cada chamada. O rótulo nunca calibra evento, limiar ou capacidade.")
    @ApiResponse(responseCode = "200", description = "Inventário")
    @ApiResponse(responseCode = "403", description = "Sem perfil ADMIN")
    public ResponseEntity<LabelInventoryDTO> rebuild(
            @Parameter(description = "Raio do casamento ocorrência↔ponto para a contagem por ponto (m)")
            @RequestParam(defaultValue = "500") double pointRadiusM) {
        return ResponseEntity.ok(service.rebuild(pointRadiusM));
    }
}
