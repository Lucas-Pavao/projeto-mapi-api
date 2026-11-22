package com.projeto.mapi.controller;

import com.projeto.mapi.service.limpeza.LimpezaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Limpezas da Central 156 por ponto (docs/MODELO-DISPONIBILIDADE-GALERIA.md, seção 8). Papel ADMIN. */
@RestController
@RequestMapping("/api/admin/limpeza")
@RequiredArgsConstructor
@Tag(name = "Administração - Limpeza da drenagem", description = "Ingestão da Central 156 e última limpeza por ponto")
public class LimpezaAdminController {

    private final LimpezaService service;

    @PostMapping("/ingestao")
    @Operation(summary = "Ingere os CSV anuais da 156 desde o ano dado (carga inicial: 2012); idempotente")
    public ResponseEntity<LimpezaService.Resultado> ingerir(@RequestParam(defaultValue = "2012") int anoInicial) throws Exception {
        return ResponseEntity.ok(service.ingerir(anoInicial));
    }

    @GetMapping
    @Operation(summary = "Chamados e última limpeza casados por ponto, e a taxa de reentupimento configurada")
    public Map<String, Object> resumo() {
        return service.resumo();
    }
}
