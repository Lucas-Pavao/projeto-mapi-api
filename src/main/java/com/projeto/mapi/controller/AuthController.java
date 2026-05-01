package com.projeto.mapi.controller;

import com.projeto.mapi.dto.LoginRequest;
import com.projeto.mapi.dto.LoginResponse;
import com.projeto.mapi.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        // Para o protótipo de mestrado, vamos usar um usuário admin fixo
        // Em produção, aqui você validaria contra o banco de dados
        if ("admin".equals(request.getUsername()) && "mapi123".equals(request.getPassword())) {
            String token = jwtService.generateToken(request.getUsername());
            return ResponseEntity.ok(new LoginResponse(token));
        }
        return ResponseEntity.status(401).build();
    }
}
