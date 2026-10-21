package com.projeto.mapi.service.admin.impl;

import com.projeto.mapi.dto.InviteRequestDTO;
import com.projeto.mapi.dto.InviteResponseDTO;
import com.projeto.mapi.exception.InvalidRequestException;
import com.projeto.mapi.model.Role;
import com.projeto.mapi.model.UserInvite;
import com.projeto.mapi.repository.UserInviteRepository;
import com.projeto.mapi.service.admin.InviteService;
import com.projeto.mapi.service.ingestion.Hashing;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class InviteServiceImpl implements InviteService {

    static final Duration VALIDITY = Duration.ofHours(72);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserInviteRepository inviteRepository;
    private final Clock clock;

    @Override
    @Transactional
    public InviteResponseDTO create(InviteRequestDTO request, String actingUsername) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        Instant now = clock.instant();
        UserInvite invite = inviteRepository.save(UserInvite.builder()
                .tokenHash(Hashing.sha256(token))
                .role(Role.valueOf(request.role()))
                .email(request.email())
                .createdBy(actingUsername)
                .createdAt(now)
                .expiresAt(now.plus(VALIDITY))
                .build());
        return new InviteResponseDTO(token, invite.getRole().name(), invite.getExpiresAt());
    }

    @Override
    @Transactional
    public String redeem(String token, String newUsername) {
        String hash = Hashing.sha256(token);
        int consumed = inviteRepository.consume(hash, clock.instant(), newUsername);
        if (consumed != 1) {
            throw new InvalidRequestException("Convite inválido, já usado ou vencido");
        }
        return inviteRepository.findByTokenHash(hash).orElseThrow().getRole().name();
    }
}
