package com.projeto.mapi.controller;

import com.projeto.mapi.dto.PointRainRebuildDTO;
import com.projeto.mapi.service.analise.PointRainService;
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

import java.time.Instant;

/** Chuva por ponto a partir do Cemaden PED (ADR 0022). Papel ADMIN (prefixo /api/admin). Síncrono. */
@RestController
@RequestMapping("/api/admin/chuva-ponto")
@RequiredArgsConstructor
@Tag(name = "Administração - Chuva por ponto", description = "Checagem espacial mensal e série horária por ponto (ADR 0022)")
public class PointRainAdminController {

    private final PointRainService service;

    @PostMapping("/rebuild")
    @Operation(summary = "Recalcula a checagem espacial mensal, a série horária de cada ponto e o vínculo estação↔ponto",
            description = "Estação reprovada no mês (pluviômetro travado, volume muito abaixo ou acima das vizinhas, "
                    + "cobertura parcial dos blocos) sai do mês, com o motivo gravado em station_month_check. A hora do "
                    + "ponto é o maior valor entre as aprovadas dentro do raio do vínculo; cada linha de "
                    + "point_rain_hourly declara a estação e quantas eram válidas. Derivado: apaga e refaz a faixa.")
    @ApiResponse(responseCode = "200", description = "Contagens e regras usadas")
    @ApiResponse(responseCode = "400", description = "Janela inválida (exige hora cheia UTC e from < to)")
    @ApiResponse(responseCode = "403", description = "Sem perfil ADMIN")
    public ResponseEntity<PointRainRebuildDTO> rebuild(
            @Parameter(description = "Início (exclusivo) das horas, UTC, hora cheia", required = true) @RequestParam Instant from,
            @Parameter(description = "Fim (inclusivo) das horas, UTC, hora cheia", required = true) @RequestParam Instant to) {
        return ResponseEntity.ok(service.rebuild(from, to));
    }
}
