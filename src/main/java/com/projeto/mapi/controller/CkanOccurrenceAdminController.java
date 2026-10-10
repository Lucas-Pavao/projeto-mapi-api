package com.projeto.mapi.controller;

import com.projeto.mapi.service.ingestion.IngestionRunView;
import com.projeto.mapi.service.ingestion.occurrence.OccurrenceIngestionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Manutenção das ocorrências CKAN (F2-BACK-18 e F2-BACK-20). Papel ADMIN (prefixo /api/admin). Síncrono. */
@RestController
@RequestMapping("/api/admin/ingestion/ckan")
@RequiredArgsConstructor
@Tag(name = "Administração - Ocorrências CKAN", description = "Reprocessamento do bruto e preenchimento do nível de geocodificação")
public class CkanOccurrenceAdminController {

    private final OccurrenceIngestionService occurrences;

    @PostMapping("/revalidate")
    @Operation(summary = "REVALIDATE de um recurso CKAN a partir do bruto já gravado (sem acessar o portal); síncrono")
    @ApiResponse(responseCode = "200", description = "ingestion_run concluído (status OK ou FAILED)")
    public ResponseEntity<IngestionRunView> revalidate(@RequestParam String resourceId,
                                                       @RequestParam(required = false) String packageId) {
        return ResponseEntity.ok(occurrences.revalidate(packageId, resourceId));
    }

    @PostMapping("/backfill-geocode-level")
    @Operation(summary = "Preenche geocode_level e neighborhood das ocorrências legadas (verificável ou NULL); síncrono")
    @ApiResponse(responseCode = "200", description = "Resumo do preenchimento")
    public ResponseEntity<OccurrenceIngestionService.GeocodeLevelBackfill> backfillGeocodeLevel() {
        return ResponseEntity.ok(occurrences.backfillGeocodeLevels());
    }
}
