package com.projeto.mapi.controller;

import com.projeto.mapi.dto.RainEventSummaryTableDTO;
import com.projeto.mapi.dto.RainEventTableDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.service.analise.RainEventClassifier;
import com.projeto.mapi.service.analise.RainEventParams;
import com.projeto.mapi.service.analise.RainEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Locale;

/**
 * Tabela de eventos de chuva por ponto crítico e resumo por tipo (ADR 0017, {@code docs/EVENTOS-DE-CHUVA.md}).
 * Insumo do modelo estocástico da galeria como sistema reparável. Só leitura.
 */
@RestController
@RequestMapping("/api/analise/eventos-chuva")
@RequiredArgsConstructor
@Tag(name = "Análise", description = "Eventos de chuva extraídos da série consolidada (ADR 0017)")
public class RainEventController {

    private static final String HEADER = "X-Dataset-Id";
    private static final String CSV = "text/csv";

    private final RainEventService service;

    @GetMapping
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Tabela de eventos de chuva (uma linha por evento)",
            description = "Detecta eventos na grade horária (hora rotulada pelo fim, UTC) da fonte escolhida. "
                    + "format=json devolve {manifest, events}; format=csv devolve o CSV. Nos dois, X-Dataset-Id = "
                    + "SHA-256 do CSV, que também é manifest.datasetId. A coluna ckanOccurrenceSameDay é só para "
                    + "validação: não calibrar parâmetro olhando para ela.")
    @ApiResponse(responseCode = "200", description = "OK",
            headers = @Header(name = HEADER, description = "SHA-256 do CSV (identidade do dataset)"),
            content = {@Content(mediaType = "application/json", schema = @Schema(implementation = RainEventTableDTO.class)),
                    @Content(mediaType = CSV, schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem autenticação ou sem perfil", content = @Content)
    @ApiResponse(responseCode = "404", description = "Ponto inexistente", content = @Content)
    public ResponseEntity<?> events(
            @Parameter(description = "Slug do ponto crítico ou 'all'", required = true) @RequestParam String slug,
            @Parameter(description = "Início da janela, instante UTC", required = true) @RequestParam Instant from,
            @Parameter(description = "Fim da janela, instante UTC", required = true) @RequestParam Instant to,
            @Parameter(description = "ANA ou OPEN_METEO_ARCHIVE") @RequestParam(defaultValue = "ANA") String source,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_DRY_GAP_HOURS) int dryGapHours,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_MIN_EVENT_MM) double minEventMm,
            @Parameter(description = "IDF ou METEOROLOGICA") @RequestParam(defaultValue = "IDF") String classification,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_IDF_THRESHOLD_MM_H) double idfThresholdMmH,
            @Parameter(description = "Capacidade de projeto em chuva equivalente (mm/h); padrão TR 5 anos, 60 min")
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_CAPACITY_MM_H) double capacityMmH,
            @Parameter(description = "Coeficiente de escoamento C")
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_RUNOFF_COEFFICIENT) double runoffCoefficient,
            @Parameter(description = "Soleira no referencial da maré (NR DHN, m); sem valor = coluna vazia")
            @RequestParam(required = false) Double tideSillM,
            @Parameter(description = "Amplitude diária a partir da qual a maré é de sizígia (m)")
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_SPRING_RANGE_M) double springRangeM,
            @Parameter(description = "json ou csv") @RequestParam(defaultValue = "json") String format) {
        var params = params(slug, from, to, source, dryGapHours, minEventMm, classification, idfThresholdMmH,
                capacityMmH, runoffCoefficient, tideSillM, springRangeM);
        var result = service.events(params);
        return respond(format, result.table().manifest().datasetId(), result.csv(), result.table(),
                "eventos_chuva_" + slug + "_" + params.source().name() + ".csv");
    }

    @GetMapping("/resumo")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Resumo dos eventos de chuva por tipo (FRACA, MEDIA, FORTE, TOTAL) e ponto",
            description = "Mesmos parâmetros da tabela. Eventos truncados ficam de fora; count inclui eventos com "
                    + "lacuna e countWithoutGap os exclui. X-Dataset-Id = SHA-256 do CSV do resumo.")
    @ApiResponse(responseCode = "200", description = "OK",
            headers = @Header(name = HEADER, description = "SHA-256 do CSV do resumo"),
            content = {@Content(mediaType = "application/json",
                    schema = @Schema(implementation = RainEventSummaryTableDTO.class)),
                    @Content(mediaType = CSV, schema = @Schema(type = "string"))})
    @ApiResponse(responseCode = "400", description = "Parâmetro inválido", content = @Content)
    @ApiResponse(responseCode = "403", description = "Sem autenticação ou sem perfil", content = @Content)
    @ApiResponse(responseCode = "404", description = "Ponto inexistente", content = @Content)
    public ResponseEntity<?> summary(
            @RequestParam String slug, @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam(defaultValue = "ANA") String source,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_DRY_GAP_HOURS) int dryGapHours,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_MIN_EVENT_MM) double minEventMm,
            @RequestParam(defaultValue = "IDF") String classification,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_IDF_THRESHOLD_MM_H) double idfThresholdMmH,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_CAPACITY_MM_H) double capacityMmH,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_RUNOFF_COEFFICIENT) double runoffCoefficient,
            @RequestParam(required = false) Double tideSillM,
            @RequestParam(defaultValue = "" + RainEventParams.DEFAULT_SPRING_RANGE_M) double springRangeM,
            @RequestParam(defaultValue = "json") String format) {
        var params = params(slug, from, to, source, dryGapHours, minEventMm, classification, idfThresholdMmH,
                capacityMmH, runoffCoefficient, tideSillM, springRangeM);
        var result = service.summary(params);
        return respond(format, result.table().manifest().datasetId(), result.csv(), result.table(),
                "eventos_chuva_resumo_" + slug + "_" + params.source().name() + ".csv");
    }

    private static RainEventParams params(String slug, Instant from, Instant to, String source, int dryGapHours,
                                          double minEventMm, String classification, double idf, double capacity,
                                          double c, Double sill, double springRange) {
        return new RainEventParams(slug, from, to, parse(RainEventParams.RainSource.class, source, "source"),
                dryGapHours, minEventMm, parse(RainEventClassifier.Classification.class, classification, "classification"),
                idf, capacity, c, sill, springRange);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String name) {
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(name + " inválido: " + value);
        }
    }

    private static ResponseEntity<?> respond(String format, String datasetId, String csv, Object json, String filename) {
        switch (format.toLowerCase(Locale.ROOT)) {
            case "csv" -> {
                return ResponseEntity.ok()
                        .header(HEADER, datasetId)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filename)
                        .contentType(MediaType.parseMediaType(CSV))
                        .body(csv);
            }
            case "json" -> {
                return ResponseEntity.ok().header(HEADER, datasetId).contentType(MediaType.APPLICATION_JSON).body(json);
            }
            default -> throw new InvalidRequestException("format deve ser json ou csv");
        }
    }
}
