package com.projeto.mapi.controller;

import com.projeto.mapi.dto.RainForecastAccuracyDTO;
import com.projeto.mapi.dto.RainForecastDTO;
import com.projeto.mapi.service.flood.RainForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/rain-forecast")
@RequiredArgsConstructor
@Validated
@Tag(name = "Previsão de chuva", description = "Chuva prevista por ponto e conferência contra a chuva medida")
public class RainForecastController {

    private final RainForecastService service;

    @GetMapping("/latest")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Última previsão de chuva gravada de cada ponto (1, 3 e 6 h)",
            description = "Leitura pura: quem prevê é o ciclo de coleta. Quantis em mm no intervalo (issuedAt, validUntil]. "
                    + "É previsão, não medição. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.")
    @ApiResponse(responseCode = "200", description = "OK; lista vazia se nada foi emitido ainda")
    public ResponseEntity<List<RainForecastDTO>> latest() {
        return ResponseEntity.ok(service.latest());
    }

    @GetMapping("/accuracy")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Acerto da previsão de chuva nos últimos dias, contra a chuva medida",
            description = "Atualizado pela conferência diária. Erro de quantil (pinball) e cobertura dos quantis por horizonte.")
    @ApiResponse(responseCode = "200", description = "OK; horizons vazio enquanto não houver previsão conferida")
    @ApiResponse(responseCode = "400", description = "days fora de 1 a 365")
    public ResponseEntity<RainForecastAccuracyDTO> accuracy(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int days) {
        return ResponseEntity.ok(service.accuracy(days));
    }
}
