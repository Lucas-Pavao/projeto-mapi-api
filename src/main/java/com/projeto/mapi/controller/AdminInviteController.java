package com.projeto.mapi.controller;

import com.projeto.mapi.dto.InviteRequestDTO;
import com.projeto.mapi.dto.InviteResponseDTO;
import com.projeto.mapi.model.User;
import com.projeto.mapi.service.admin.InviteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/invites")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Usuários e convites (ADMIN)")
public class AdminInviteController {

    private final InviteService inviteService;

    @PostMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Gera convite de uso único (72 h) com perfil pré-definido (ADMIN)")
    @ApiResponse(responseCode = "201", description = "Convite criado; o token só aparece nesta resposta")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<InviteResponseDTO> create(@Valid @RequestBody InviteRequestDTO request,
                                                    @AuthenticationPrincipal User acting) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteService.create(request, acting.getUsername()));
    }
}
