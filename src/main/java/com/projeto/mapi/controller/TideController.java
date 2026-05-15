package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TideTableResponseDTO;
import com.projeto.mapi.service.TideService;
import com.projeto.mapi.service.PdfConversionService;
import com.projeto.mapi.service.TideIngestionService;
import com.projeto.mapi.service.NavyScraperService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/tide")
@RequiredArgsConstructor
@Slf4j
public class TideController {
    private final TideService tideService;
    private final PdfConversionService pdfConversionService;
    private final TideIngestionService tideIngestionService;
    private final NavyScraperService navyScraperService;

    @GetMapping("/{harbor}")
    public ResponseEntity<TideTableResponseDTO> getTideTable(
            @PathVariable String harbor,
            @RequestParam(required = false) Integer year) {
        
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Buscando tábua de maré para o porto: {} e ano: {}", harbor, queryYear);

        Optional<TideTableResponseDTO> tideTable = tideService.getTideTable(harbor, queryYear);
        
        return tideTable.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/state/{state}")
    public ResponseEntity<List<TideTableResponseDTO>> getTideByState(
            @PathVariable String state,
            @RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Buscando tábuas de maré para o estado: {} e ano: {}", state, queryYear);
        List<TideTableResponseDTO> results = tideService.getTideTablesByState(state, queryYear);
        return ResponseEntity.ok(results);
    }

    @GetMapping("/search")
    public ResponseEntity<List<TideTableResponseDTO>> searchTide(
            @RequestParam String harbor,
            @RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Pesquisando portos por nome: {} e ano: {}", harbor, queryYear);
        List<List<TideTableResponseDTO>> results = List.of(tideService.searchTideTablesByHarbor(harbor, queryYear));
        return ResponseEntity.ok(results.get(0));
    }

    @GetMapping("/harbors")
    public ResponseEntity<List<String>> listHarbors(@RequestParam(required = false) Integer year) {
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        log.info("Listando todos os portos disponíveis para o ano: {}", queryYear);
        List<String> results = tideService.getAllHarbors(queryYear);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/ingest/automatic")
    public ResponseEntity<List<TideTableResponseDTO>> triggerAutomaticIngestion(@RequestParam(required = false) Integer year) {
        log.info("Disparando ingestão automática via site da Marinha.");
        try {
            int queryYear = (year != null) ? year : java.time.Year.now().getValue();
            List<TideTableResponseDTO> results = navyScraperService.scrapeAndIngestPernambuco(queryYear);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Erro na ingestão automática: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/html")
    public ResponseEntity<List<TideTableResponseDTO>> ingestFromHtml(
            @RequestBody String html,
            @RequestParam(required = false) Integer year) {
        log.info("Recebido HTML manual para processamento.");
        try {
            int queryYear = (year != null) ? year : java.time.Year.now().getValue();
            List<TideTableResponseDTO> results = navyScraperService.ingestFromHtml(html, queryYear);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            log.error("Erro na ingestão via HTML: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TideTableResponseDTO> uploadPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "state", required = false) String state,
            @RequestParam(value = "year", required = false) Integer year) {
        log.info("Upload manual de PDF recebido.");
        try {
            TideTableResponseDTO result = pdfConversionService.convertAndSave(file, state, year);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erro ao converter e salvar PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/local")
    public ResponseEntity<TideTableResponseDTO> ingestLocalRecife(@RequestParam(required = false) Integer year) {
        log.info("Comando para ingestão de arquivo local acionado.");
        try {
            int queryYear = (year != null) ? year : java.time.Year.now().getValue();
            TideTableResponseDTO result = tideIngestionService.ingestRecifeTide(queryYear);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erro na ingestão local: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }
}
