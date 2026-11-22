package com.projeto.mapi.controller;

import com.projeto.mapi.service.weather.OpenMeteoModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Open-Meteo por modelo (prompt dos horizontes, bloco B). Papel ADMIN (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/open-meteo")
@RequiredArgsConstructor
@Tag(name = "Administração - Open-Meteo por modelo", description = "Rodadas do ECMWF, ICON e do modelo atual; histórico de rodadas")
public class OpenMeteoModelAdminController {

    private final OpenMeteoModelService service;

    @PostMapping("/historico")
    @Operation(summary = "Carrega o histórico de rodadas (1, 2 e 3 dias de antecedência) desde a data; retomável, em segundo plano",
            description = "Uma requisição por vez, com pausa. O previous-runs-api começa em 2024-01-19 (ICON e best_match) e "
                    + "2024-02-03 (ECMWF 0,25°); antes disso as horas vêm vazias e não viram linha.")
    @ApiResponse(responseCode = "202", description = "carga iniciada (ou já em andamento)")
    public ResponseEntity<Map<String, String>> historico(
            @RequestParam(defaultValue = "2024-01-15") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {
        service.backfillAsync(desde);
        return ResponseEntity.accepted().body(Map.of("status", "iniciada", "desde", desde.toString()));
    }

    @GetMapping("/modelos")
    @Operation(summary = "Linhas gravadas por modelo e produto, com a hora mais nova e a última publicação")
    public List<Map<String, Object>> modelos() {
        return service.summary();
    }
}
