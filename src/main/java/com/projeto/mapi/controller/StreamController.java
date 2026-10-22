package com.projeto.mapi.controller;

import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.service.stream.LiveStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/stream")
@RequiredArgsConstructor
@Tag(name = "Ao vivo", description = "Stream de eventos (SSE, ADR 0012)")
public class StreamController {

    private final LiveStreamService streamService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('USER')")
    @Operation(summary = "Stream de eventos ao vivo (SSE)",
            description = "Eventos: prediction, sensor, risk-summary, alert (só OPERADOR e ADMIN) e heartbeat a cada 25 s. "
                    + "Suporta Last-Event-ID. Máximo de 3 conexões por usuário.")
    @ApiResponse(responseCode = "200", description = "Stream aberto")
    @ApiResponse(responseCode = "429", description = "Mais de 3 conexões do mesmo usuário")
    public SseEmitter stream(@AuthenticationPrincipal User user,
                             @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        boolean alerts = user.getRole() == Role.OPERADOR || user.getRole() == Role.ADMIN;
        return streamService.subscribe(user.getUsername(), alerts, lastEventId);
    }
}
