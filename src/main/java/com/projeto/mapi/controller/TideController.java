package com.projeto.mapi.controller;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.service.TideService;
import com.projeto.mapi.service.PdfConversionService;
import com.projeto.mapi.service.TideIngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Optional;

@RestController
@RequestMapping("/api/tide")
@RequiredArgsConstructor
@Slf4j
public class TideController {
    private final TideService tideService;
    private final PdfConversionService pdfConversionService;
    private final TideIngestionService tideIngestionService;

    @GetMapping("/{harbor}")
    public ResponseEntity<TideTable> getTideTable(
            @PathVariable String harbor,
            @RequestParam(required = false) Integer year) {
        
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Buscando tábua de maré para o porto: {} e ano: {}", harbor, queryYear);

        Optional<TideTable> tideTable = tideService.getTideTable(harbor, queryYear);
        
        return tideTable.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TideTable> uploadPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "year", required = false) Integer year) {
        log.info("Upload manual de PDF recebido.");
        try {
            TideTable result = pdfConversionService.convertAndSave(file, state, year);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erro ao converter e salvar PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/local")
    public ResponseEntity<TideTable> ingestLocalRecife(@RequestParam(required = false) Integer year) {
        log.info("Comando para ingestão de arquivo local acionado.");
        try {
            int queryYear = (year != null) ? year : java.time.Year.now().getValue();
            TideTable result = tideIngestionService.ingestRecifeTide(queryYear);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erro na ingestão local: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
