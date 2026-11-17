package com.projeto.mapi.controller;

import com.projeto.mapi.dto.AlertaLatenciaDTO;
import com.projeto.mapi.dto.AlertaPontoDTO;
import com.projeto.mapi.dto.AlertaTransicaoDTO;
import com.projeto.mapi.service.alerta.AlertaQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/alerta")
@RequiredArgsConstructor
@Validated
@Tag(name = "Alerta por ponto", description = "Alerta de chuva forte (rain_alert) e alagamento provável (point_flood), ADR 0026")
public class AlertaController {

    private final AlertaQueryService service;
    private final com.projeto.mapi.service.alerta.PointRain10MinExportService export10;
    private final com.projeto.mapi.service.alerta.AlertaConferenciaService conferencia;

    /** Em modo sombra o alerta só aparece para ADMIN, marcado "em teste" (ADR 0026). */
    private void checkShadow(Authentication auth) {
        if (service.shadow() && auth.getAuthorities().stream().noneMatch(a -> "ADMIN".equals(a.getAuthority()))) {
            throw new AccessDeniedException("Alerta em modo sombra: visível só para administrador");
        }
    }

    @GetMapping("/pontos")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Estado atual do alerta de cada ponto ativo",
            description = "rain_alert (NENHUM, ATENCAO, ALERTA, SEM_DADO) e point_flood (NAO, PROVAVEL, SEM_DADO), com motivo, "
                    + "valores que dispararam, versões, fontes e idade da leitura. Leitura pura. DADOS DA REDE OBSERVACIONAL DO CEMADEN/MCTIC.")
    @ApiResponse(responseCode = "200", description = "OK; lista vazia antes do primeiro ciclo")
    @ApiResponse(responseCode = "403", description = "modo sombra e o usuário não é administrador")
    public ResponseEntity<List<AlertaPontoDTO>> current(Authentication auth) {
        checkShadow(auth);
        return ResponseEntity.ok(service.current());
    }

    @GetMapping("/transicoes")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Histórico de transições do alerta", description = "Mais recente primeiro. eventId agrupa o mesmo episódio de chuva.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "modo sombra e o usuário não é administrador")
    @ApiResponse(responseCode = "404", description = "ponto inexistente")
    public ResponseEntity<List<AlertaTransicaoDTO>> transitions(Authentication auth,
                                                                @RequestParam(required = false) String ponto,
                                                                @RequestParam(defaultValue = "48") @Min(1) @Max(24 * 90) int horas,
                                                                @RequestParam(defaultValue = "200") @Min(1) @Max(5000) int limite) {
        checkShadow(auth);
        return ResponseEntity.ok(service.transitions(ponto, horas, limite));
    }

    @GetMapping("/latencia")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Atraso do caminho do alerta por trecho",
            description = "Mediana, p90 e máximo (s) de estação→coleta, coleta→banco, banco→ciclo, ciclo→IA, IA→estado, "
                    + "estado→SSE e total, separando hora com chuva (>= 0,2 mm) de hora seca. Orçamento: p90 do total abaixo de 5 min em hora com chuva.")
    @ApiResponse(responseCode = "200", description = "OK")
    public ResponseEntity<AlertaLatenciaDTO> latency(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int dias) {
        return ResponseEntity.ok(service.latency(dias));
    }

    @GetMapping(value = "/serie-10min.csv", produces = "text/csv")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Série de chuva do ponto em passos de 10 min (CSV)",
            description = "Mesma regra do alerta: maior valor entre as estações válidas a até 5 km, reprovada no mês fora, pico "
                    + "isolado fora; bloco longo espalhado e marcado. Passo sem estação fica vazio (nunca zero). Por mês.")
    @ApiResponse(responseCode = "200", description = "CSV")
    @ApiResponse(responseCode = "404", description = "ponto inexistente")
    public void serie10min(@RequestParam String ponto, @RequestParam java.time.Instant de, @RequestParam java.time.Instant ate,
                           jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
        resp.setContentType("text/csv");
        resp.setCharacterEncoding("UTF-8");
        export10.writeCsv(ponto, de, ate, resp.getWriter());
    }

    @GetMapping("/conferencia")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Conferência diária do alerta nos últimos dias",
            description = "Eventos FORTE medidos, quantos tiveram alerta antes, antecedência real, falsos por evento, tempo em "
                    + "SEM_DADO e episódios de ALAGAMENTO_PROVAVEL por ponto (sem verdade de campo: guardados para cruzar com ocorrências).")
    @ApiResponse(responseCode = "200", description = "OK")
    public ResponseEntity<com.projeto.mapi.dto.AlertaConferenciaDTO> conferencia(@RequestParam(defaultValue = "30") @Min(1) @Max(365) int dias) {
        return ResponseEntity.ok(conferencia.summary(dias));
    }

    @org.springframework.web.bind.annotation.PostMapping("/conferencia/{dia}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Confere de novo um dia (UTC)", description = "Mesma conta da conferência diária das 03:35 UTC.")
    @ApiResponse(responseCode = "200", description = "pontos conferidos")
    public ResponseEntity<java.util.Map<String, Integer>> conferirDia(@org.springframework.web.bind.annotation.PathVariable java.time.LocalDate dia) {
        return ResponseEntity.ok(java.util.Map.of("pontos", conferencia.checkDay(dia)));
    }

    @GetMapping("/latencia/relatorios")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Relatórios de atraso dos dias com chuva acima de 10 mm em algum ponto",
            description = "Gerados sozinhos pela conferência diária; cada um traz mediana, p90 e máximo por trecho.")
    @ApiResponse(responseCode = "200", description = "OK; vazio enquanto não houver dia com chuva")
    public ResponseEntity<List<java.util.Map<String, Object>>> relatorios() {
        return ResponseEntity.ok(conferencia.reports());
    }
}
