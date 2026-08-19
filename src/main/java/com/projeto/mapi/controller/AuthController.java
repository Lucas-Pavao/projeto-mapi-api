package com.projeto.mapi.controller;

import com.projeto.mapi.config.AppProperties;
import com.projeto.mapi.dto.LoginRequest;
import com.projeto.mapi.dto.LoginResponse;
import com.projeto.mapi.dto.RegisterRequest;
import com.projeto.mapi.dto.TokenRefreshRequest;
import com.projeto.mapi.dto.UserInfoResponse;
import com.projeto.mapi.model.User;
import com.projeto.mapi.security.JwtService;
import com.projeto.mapi.service.AuthenticationService;
import com.projeto.mapi.service.RefreshTokenService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenService refreshTokenService;
    private final AppProperties appProperties;

    // Refresh token só é enviado pelo navegador pra endpoints sob este path — não vaza pra
    // todas as outras chamadas de API, só onde é realmente necessário (renovar ou revogar).
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    @PostMapping("/register")
    public ResponseEntity<Void> register(@RequestBody RegisterRequest request) {
        authenticationService.register(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse loginResponse = authenticationService.login(request);
        setAuthCookies(response, loginResponse);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(
            @RequestBody(required = false) TokenRefreshRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        String refreshTokenValue = (request != null && request.getRefreshToken() != null)
                ? request.getRefreshToken()
                : extractCookie(httpRequest, "refreshToken");

        LoginResponse loginResponse = authenticationService.refreshToken(new TokenRefreshRequest(refreshTokenValue));
        setAuthCookies(response, loginResponse);
        return ResponseEntity.ok(loginResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String refreshTokenValue = extractCookie(request, "refreshToken");
        if (refreshTokenValue != null) {
            refreshTokenService.deleteByToken(refreshTokenValue);
        }
        clearAuthCookies(response);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserInfoResponse> me(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(new UserInfoResponse(user.getUsername(), user.getRole().name()));
    }

    private void setAuthCookies(HttpServletResponse response, LoginResponse loginResponse) {
        boolean secure = appProperties.getCookie().isSecure();

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", loginResponse.getAccessToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(JwtService.ACCESS_TOKEN_EXPIRATION_MS / 1000)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", loginResponse.getRefreshToken())
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(java.time.Duration.ofDays(30))
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private void clearAuthCookies(HttpServletResponse response) {
        boolean secure = appProperties.getCookie().isSecure();

        ResponseCookie accessCookie = ResponseCookie.from("accessToken", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();

        ResponseCookie refreshCookie = ResponseCookie.from("refreshToken", "")
                .httpOnly(true)
                .secure(secure)
                .sameSite("Lax")
                .path(REFRESH_COOKIE_PATH)
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    private String extractCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
