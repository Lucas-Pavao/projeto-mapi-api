package com.projeto.mapi.controller;

import com.projeto.mapi.dto.LocationAnalysisDTO;
import com.projeto.mapi.service.location.LocationAnalysisService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/** Análise de local do mapa. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC. */
@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
@Tag(name = "Análise de local", description = "Chuva, ponto crítico, maré, rio, terreno e clima de uma coordenada")
public class LocationController {

    private final LocationAnalysisService analysis;

    @GetMapping("/analysis")
    @Operation(summary = "Análise de um local: chuva do raio de 5 km, ponto crítico mais próximo, maré, rio, terreno e clima",
            description = "Chuva pela regra do ponto (chuva-ponto-v4, maior valor entre as estações válidas até 5 km): "
                    + "MEASURED, GAP (há estação válida sem valor; não estima) ou ESTIMATED (nenhuma estação válida; Open-Meteo). "
                    + "nearestPoint traz a última previsão gravada (não chama a IA). river: régua da APAC a até 6 km ou null. "
                    + "terrain: cota do MDT do PE3D, null fora da cobertura. A resposta é calculada no centro da célula de ~200 m "
                    + "da coordenada, com cache de 10 min. inArea = retângulo da RMR; isSea = dentro da área, fora do MDT e "
                    + "altitude do Open-Meteo <= 0. Instantes em UTC; ausente = null, nunca zero.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "lat/lon ausentes, não numéricos ou fora de -90..90 / -180..180")
    public ResponseEntity<LocationAnalysisDTO> analysis(
            @Parameter(description = "Latitude, graus decimais") @RequestParam double lat,
            @Parameter(description = "Longitude, graus decimais") @RequestParam double lon,
            @Parameter(description = "Instante de referência, ISO-8601 UTC; padrão: agora") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant at) {
        return ResponseEntity.ok(analysis.analyze(lat, lon, at));
    }
}
