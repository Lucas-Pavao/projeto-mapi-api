package com.projeto.mapi.controller;

import com.projeto.mapi.model.TideTable;
import com.projeto.mapi.service.TideService;
import com.projeto.mapi.service.PdfConversionService;
import com.projeto.mapi.service.TideIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/tide")
@RequiredArgsConstructor
public class TideController {
    private final TideService tideService;
    private final PdfConversionService pdfConversionService;
    private final TideIngestionService tideIngestionService;

    @GetMapping("/{harbor}")
    public ResponseEntity<TideTable> getTideTable(
            @PathVariable String harbor,
            @RequestParam(required = false) Integer year) {
        
        int queryYear = (year != null) ? year : java.time.Year.now().getValue();
        
        return tideService.getTideTable(harbor, queryYear)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/upload")
    public ResponseEntity<TideTable> uploadPdf(
            @RequestParam("file") MultipartFile file,
            @RequestParam("state") String state,
            @RequestParam("year") Integer year) {
        try {
            TideTable result = pdfConversionService.convertAndSave(file, state, year);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/ingest/recife")
    public ResponseEntity<TideTable> ingestRecife(@RequestParam(required = false) Integer year) {
        try {
            int queryYear = (year != null) ? year : java.time.Year.now().getValue();
            TideTable result = tideIngestionService.ingestRecifeTide(queryYear);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
