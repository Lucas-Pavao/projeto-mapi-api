package com.projeto.mapi.service;

import com.projeto.mapi.model.RefreshToken;
import java.util.Optional;

public interface RefreshTokenService {
    Optional<RefreshToken> findByToken(String token);
    RefreshToken createRefreshToken(String username);
    RefreshToken verifyExpiration(RefreshToken token);
    void deleteByUserId(Long userId);
}
