package com.projeto.mapi.controller;

import com.projeto.mapi.dto.PageResponseDTO;
import com.projeto.mapi.dto.UserPatchRequestDTO;
import com.projeto.mapi.dto.UserSummaryDTO;
import com.projeto.mapi.model.User;
import com.projeto.mapi.service.admin.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Validated
@Tag(name = "Admin", description = "Usuários e convites (ADMIN)")
public class AdminUserController {

    private final UserAdminService userAdminService;

    @GetMapping
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Lista usuários (ADMIN)")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "403", description = "Perfil insuficiente (ADR 0010)")
    public ResponseEntity<PageResponseDTO<UserSummaryDTO>> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(userAdminService.list(page, size));
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(summary = "Troca perfil ou ativa/desativa usuário (ADMIN). Não vale para si mesmo.")
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Usuário inexistente")
    @ApiResponse(responseCode = "409", description = "Deixaria o sistema sem ADMIN ativo, ou alvo é o próprio usuário")
    public ResponseEntity<UserSummaryDTO> update(@PathVariable Long id,
                                                 @Valid @RequestBody UserPatchRequestDTO patch,
                                                 @AuthenticationPrincipal User acting) {
        return ResponseEntity.ok(userAdminService.update(id, patch, acting.getUsername()));
    }
}
