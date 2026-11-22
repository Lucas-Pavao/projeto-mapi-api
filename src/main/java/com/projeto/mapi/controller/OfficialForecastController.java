package com.projeto.mapi.controller;

import com.projeto.mapi.dto.OfficialForecastDTO;
import com.projeto.mapi.service.warning.OfficialForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Previsão oficial do INMET por município (prompt dos horizontes, bloco A). A mesma leitura em duas rotas: a do app
 * (exige login) e a pública da página Minha rua ({@code /api/public/**}, informação oficial em domínio público).
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "Previsão oficial", description = "Previsão do tempo do INMET por município (informação oficial, separada da previsão do MAPi)")
public class OfficialForecastController {

    private final OfficialForecastService service;

    @GetMapping({"/api/previsao-oficial/{municipio}", "/api/public/previsao-oficial/{municipio}"})
    @Operation(summary = "Previsão vigente do INMET para o município, com o instante em que o MAPi a viu",
            description = "Leitura pura do que a coleta a cada 3 h gravou. Sem quantidade de chuva (a origem não traz). "
                    + "firstSeenAt/lastSeenAt são quando o MAPi viu cada versão, não a emissão. Sem previsão gravada ou "
                    + "com a mais nova vista há mais de 12 h, vem reason (SEM_PREVISAO, FONTE_VELHA, MUNICIPIO_DESCONHECIDO).")
    @ApiResponse(responseCode = "200", description = "OK (sem previsão, days vazio e reason preenchido)")
    public ResponseEntity<OfficialForecastDTO> current(
            @Parameter(description = "Código IBGE de 7 dígitos (ex.: 2611606) ou nome do município da RMR (ex.: Recife)")
            @PathVariable String municipio) {
        return ResponseEntity.ok(service.current(municipio));
    }
}
