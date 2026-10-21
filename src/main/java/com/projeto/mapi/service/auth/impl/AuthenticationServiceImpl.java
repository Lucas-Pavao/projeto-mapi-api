package com.projeto.mapi.service.auth.impl;

import com.projeto.mapi.dto.LoginRequest;
import com.projeto.mapi.dto.LoginResponse;
import com.projeto.mapi.dto.RegisterRequest;
import com.projeto.mapi.dto.TokenRefreshRequest;
import com.projeto.mapi.exception.TokenException;
import com.projeto.mapi.exception.UserAlreadyExistsException;
import com.projeto.mapi.model.RefreshToken;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.User;
import com.projeto.mapi.repository.UserRepository;
import com.projeto.mapi.security.JwtService;
import com.projeto.mapi.service.auth.AuthenticationService;
import com.projeto.mapi.service.auth.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.projeto.mapi.service.admin.InviteService;
import java.time.Clock;

@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenService refreshTokenService;
    private final InviteService inviteService;
    private final Clock clock;

    @Override
    @Transactional
    public void register(RegisterRequest request) {
        if (userRepository.findByUsername(request.getUsername()).isPresent()) {
            throw new UserAlreadyExistsException("Username already exists: " + request.getUsername());
        }

        // Convite (ADR 0010): o perfil vem do token (e o e-mail, se o convite trouxer); sem token, USER.
        Role role = Role.USER;
        String email = normalizeEmail(request.getEmail());
        if (request.getInviteToken() != null && !request.getInviteToken().isBlank()) {
            var redemption = inviteService.redeem(request.getInviteToken(), request.getUsername());
            role = Role.valueOf(redemption.role());
            if (redemption.email() != null && !redemption.email().isBlank()) {
                email = normalizeEmail(redemption.email());
            }
        }
        // Falha antes de gravar; se o convite já foi consumido nesta transação, o rollback o devolve.
        if (email != null && userRepository.existsByEmailIgnoreCase(email)) {
            throw new UserAlreadyExistsException("E-mail já cadastrado");
        }

        var user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(role)
                .email(email)
                .name(request.getName() == null || request.getName().isBlank() ? null : request.getName().trim())
                .build();

        userRepository.save(user);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getUsername(),
                        request.getPassword()
                )
        );

        var user = userRepository.findByUsername(request.getUsername())
                .orElseThrow();
        user.setLastLoginAt(clock.instant());
        userRepository.save(user);

        var jwtToken = jwtService.generateToken(user.getUsername());
        var refreshToken = refreshTokenService.createRefreshToken(user.getUsername());

        return LoginResponse.builder()
                .accessToken(jwtToken)
                .refreshToken(refreshToken.getToken())
                .build();
    }

    @Override
    public LoginResponse refreshToken(TokenRefreshRequest request) {
        return refreshTokenService.findByToken(request.getRefreshToken())
                .map(refreshTokenService::verifyExpiration)
                .map(RefreshToken::getUser)
                .map(user -> {
                    String token = jwtService.generateToken(user.getUsername());
                    return LoginResponse.builder()
                            .accessToken(token)
                            .refreshToken(request.getRefreshToken())
                            .build();
                })
                .orElseThrow(() -> new TokenException("Refresh token is not in database!"));
    }
}
