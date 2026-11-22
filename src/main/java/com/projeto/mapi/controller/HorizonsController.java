package com.projeto.mapi.controller;

import com.projeto.mapi.dto.HorizonsDTO;
import com.projeto.mapi.service.horizontes.HorizonsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Leitura por horizonte de um ponto (prompt dos horizontes, bloco D1). */
@RestController
@RequestMapping("/api/horizontes")
@RequiredArgsConstructor
@Tag(name = "Horizontes", description = "INMET (oficial), dia de risco e próxima hora (MAPi), lado a lado e sem combinar")
public class HorizonsController {

    private final HorizonsService service;

    @GetMapping("/{slug}")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "As três camadas do ponto: oficial (INMET), dia de risco e próxima hora (MAPi)",
            description = "Nenhuma camada é combinada com outra. O dia de risco vem ausente com motivo NAO_VALIDADO enquanto "
                    + "não passar no critério do bloco C. Em modo sombra, quem não é administrador recebe nextHour.inTest.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "ponto não encontrado")
    public ResponseEntity<HorizonsDTO> forPoint(@PathVariable String slug, Authentication auth) {
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ADMIN".equals(a.getAuthority()));
        return ResponseEntity.ok(service.forPoint(slug, admin));
    }
}
