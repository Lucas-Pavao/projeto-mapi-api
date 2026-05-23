package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TabuaMareResponse;
import com.projeto.mapi.service.TabuaMareService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/tabua-mare")
@RequiredArgsConstructor
@Tag(name = "Tabua de Maré (DevTu)", description = "Integração com a API tabuamare.devtu.qzz.io")
public class TabuaMareController {

    private final TabuaMareService tabuaMareService;

    @GetMapping("/states")
    @Operation(summary = "Listar estados costeiros")
    public ResponseEntity<TabuaMareResponse<List<String>>> getStates() {
        return ResponseEntity.ok(tabuaMareService.getStates());
    }

    @GetMapping("/harbors/state/{state}")
    @Operation(summary = "Listar portos por estado")
    public ResponseEntity<TabuaMareResponse<List<Object>>> getHarborNames(@PathVariable String state) {
        return ResponseEntity.ok(tabuaMareService.getHarborNames(state));
    }

    @GetMapping("/harbors/{ids}")
    @Operation(summary = "Obter informações de portos por IDs")
    public ResponseEntity<TabuaMareResponse<List<Object>>> getHarbors(@PathVariable String ids) {
        return ResponseEntity.ok(tabuaMareService.getHarbors(ids));
    }

    @GetMapping("/tide/{harbor}/{month}/{days}")
    @Operation(summary = "Obter tábua de maré por porto e período")
    public ResponseEntity<TabuaMareResponse<List<Object>>> getTideTable(
            @PathVariable String harbor,
            @PathVariable Integer month,
            @PathVariable String days) {
        return ResponseEntity.ok(tabuaMareService.getTideTable(harbor, month, days));
    }

    @GetMapping("/nearest")
    @Operation(summary = "Encontrar porto mais próximo")
    public ResponseEntity<TabuaMareResponse<Object>> getNearestHarbor(@RequestParam double latitude, @RequestParam double longitude) {
        String latLng = "[" + latitude + "," + longitude + "]";
        return ResponseEntity.ok(tabuaMareService.getNearestHarbor(latLng));
    }
}
