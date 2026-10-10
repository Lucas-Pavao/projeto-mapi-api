package com.projeto.mapi.controller;

import com.projeto.mapi.service.sensor.HistoricalDataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints destrutivos que só existem no profile "dev". Ficam em um controller à parte (e não em
 * HistoricalDataController) porque @Profile age no bean inteiro: fora de "dev" este controller
 * simplesmente não é registrado, então a rota não existe (404) e some do OpenAPI/Swagger — em vez
 * de depender de um if em runtime que poderia ser esquecido ou contornado. Apagar o banco de
 * produção por um DELETE autenticado (mesmo de ADMIN) é um risco que não vale a conveniência.
 */
@RestController
@RequestMapping("/api/admin/ingestion")
@RequiredArgsConstructor
@Profile("dev")
@Tag(name = "Administração - Ingestão", description = "Endpoints para carregar dados históricos")
public class DevAdminController {

    private final HistoricalDataService historicalDataService;

    @DeleteMapping("/wipe-database")
    @Operation(summary = "LIMPA TODO O BANCO DE DADOS (Clima, Sensores e Eventos) — disponível apenas no profile dev")
    public ResponseEntity<Void> wipeDatabase() {
        historicalDataService.wipeDatabase();
        return ResponseEntity.noContent().build();
    }
}
