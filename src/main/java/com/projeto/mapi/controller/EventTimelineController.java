package com.projeto.mapi.controller;

import com.projeto.mapi.dto.TimelineDTO;
import com.projeto.mapi.service.event.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Eventos", description = "Linha do tempo de um evento de chuva")
public class EventTimelineController {

    private final TimelineService timelineService;

    @GetMapping("/timeline")
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Linha do tempo agregada (chuva, maré, rio, risco por ponto)",
            description = "Janela máxima de 72 h, passo de 10 min; to vai até agora + 72 h. Só a maré é prevista no futuro: chuva, rio e risco (de flood_predictions) param em agora.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Janela inválida, to além de agora + 72 h ou slug inexistente")
    public ResponseEntity<TimelineDTO> timeline(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) String slugs) {
        List<String> list = slugs == null || slugs.isBlank() ? List.of()
                : Arrays.stream(slugs.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        return ResponseEntity.ok(timelineService.timeline(from, to, list));
    }
}
