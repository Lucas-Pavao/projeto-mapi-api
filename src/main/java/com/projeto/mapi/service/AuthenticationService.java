package com.projeto.mapi.service;

import com.projeto.mapi.dto.LoginRequest;
import com.projeto.mapi.dto.LoginResponse;
import com.projeto.mapi.dto.RegisterRequest;
import com.projeto.mapi.dto.TokenRefreshRequest;

public interface AuthenticationService {
    void register(RegisterRequest request);
    LoginResponse login(LoginRequest request);
    LoginResponse refreshToken(TokenRefreshRequest request);
}
