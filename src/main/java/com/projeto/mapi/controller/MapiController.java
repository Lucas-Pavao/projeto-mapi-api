package com.projeto.mapi.controller;

import com.projeto.mapi.dto.FloodPointRequestDTO;
import com.projeto.mapi.dto.FloodPointResponseDTO;
import com.projeto.mapi.dto.FloodPredictionResponseDTO;
import com.projeto.mapi.dto.MapiResponseDTO;
import com.projeto.mapi.dto.FloodScenarioLabelRequestDTO;
import com.projeto.mapi.dto.FloodScenarioLabelResponseDTO;
import com.projeto.mapi.service.MapiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "MAPI", description = "Endpoints integrados do Projeto MAPI")
public class MapiController {

    private final MapiService mapiService;

    // Dado ambiental agregado (sensores num raio de 3km, clima, maré, ondas) para uma coordenada
    // qualquer. Leitura pura: sem chamar a MAPI AI, sem gravar nada — seguro pra pollar com
    // frequência (ex: atualização em tempo real do mapa). GET porque é seguro e idempotente
    // (dentro da janela de cache).
    @GetMapping("/environmental-data")
    @Operation(summary = "Busca o dado ambiental agregado (sensores, clima, maré, ondas) para uma coordenada")
    public ResponseEntity<MapiResponseDTO> getEnvironmentalData(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(mapiService.getEnvironmentalData(latitude, longitude));
    }

    // Roda uma nova avaliação de risco de alagamento (chama a MAPI AI) e grava o resultado em
    // flood_predictions. POST porque cria um novo registro de auditoria a cada chamada — não é
    // idempotente nem "seguro" no sentido HTTP (tem efeito colateral real), então GET seria a
    // semântica errada aqui mesmo sendo só coordenadas como entrada.
    @PostMapping("/flood-predictions")
    @Operation(summary = "Roda uma nova predição de risco de alagamento da IA para as coordenadas informadas e grava a auditoria")
    public ResponseEntity<FloodPredictionResponseDTO> createFloodPrediction(
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapiService.createFloodPrediction(latitude, longitude));
    }

    @PostMapping("/pontos")
    @Operation(summary = "Registra um novo ponto de monitoramento de alagamento")
    public ResponseEntity<FloodPointResponseDTO> createFloodPoint(

            @Valid @RequestBody FloodPointRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapiService.createFloodPoint(request));
    }

    @PostMapping("/pontos/scenarios")
    @Operation(summary = "Registra observações de cenários de alagamento com variáveis ambientais consolidadas")
    public ResponseEntity<FloodScenarioLabelResponseDTO> registerScenarioLabel(
            @Valid @RequestBody FloodScenarioLabelRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(mapiService.registerScenarioLabel(request));
    }

    @GetMapping("/pontos")
    @Operation(summary = "Lista todos os pontos de monitoramento registrados")
    public ResponseEntity<List<FloodPointResponseDTO>> getAllFloodPoints() {
        return ResponseEntity.ok(mapiService.getAllFloodPoints());
    }

    @GetMapping("/pontos/{slug}")
    @Operation(summary = "Busca o status atual de um ponto específico")
    public ResponseEntity<FloodPointResponseDTO> getPointStatus(@PathVariable String slug) {
        FloodPointResponseDTO point = mapiService.getFloodPointBySlug(slug);
        if (point == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(point);
    }
}
