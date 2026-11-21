package com.projeto.mapi.controller;

import com.projeto.mapi.dto.AlagamentoPontoDTO;
import com.projeto.mapi.service.alagamento.FloodForecastService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alagamento")
@RequiredArgsConstructor
@Tag(name = "Alerta de alagamento por regressão",
        description = "Chuva prevista (rain_v1) -> balde M1a a 10 min -> regra fixa de 15 cm. Experimental, V38.")
public class AlagamentoController {

    private final FloodForecastService service;

    @GetMapping("/pontos")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Alerta de alagamento por regressão de cada ponto ativo",
            description = "level: ALERTA (lâmina provável q50 chega a 15 cm), ATENCAO (só a pessimista q95), SEM_ALERTA ou "
                    + "SEM_DADO com reason (CHUVA_AUSENTE, PREVISAO_INDISPONIVEL, SEM_TERRENO, TERRENO_EM_REVISAO, "
                    + "SERIE_INCOMPLETA). Por horizonte (1, 3 e 6 h): chuva e lâmina q50/q95, validade. explanation: SHAP local "
                    + "do rain_v1 em texto físico, contrafactual, sensibilidade e decomposição do balde a cada 10 min. Leitura pura. "
                    + "Nenhum modelo treinado com rótulo de alagamento. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.")
    @ApiResponse(responseCode = "200", description = "OK; lista vazia antes do primeiro ciclo")
    @ApiResponse(responseCode = "403", description = "modo sombra e o usuário não é administrador")
    public ResponseEntity<List<AlagamentoPontoDTO>> current(Authentication auth) {
        if (service.shadow() && auth.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Alerta de alagamento por regressão em modo sombra: visível só para administrador");
        }
        return ResponseEntity.ok(service.current());
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Versões da MAPI AI e último ciclo do alerta por regressão (tela Dados)",
            description = "rain_model_version, bucket_version, flood_version e code_version (commit) do /health da IA; último ciclo: "
                    + "instante, pontos por nível e SEM_DADO por motivo. aiReachable=false quando a IA não respondeu.")
    @ApiResponse(responseCode = "200", description = "OK")
    public ResponseEntity<com.projeto.mapi.dto.AlagamentoStatusDTO> status() {
        return ResponseEntity.ok(service.status());
    }
}
