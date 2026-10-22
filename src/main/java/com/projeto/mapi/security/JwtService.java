package com.projeto.mapi.security;

import com.projeto.mapi.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    // Exposta pra quem precisa alinhar o Max-Age do cookie httpOnly com a expiração real do
    // token (ver AuthController) — evita o "1 hora" mágico duplicado em dois lugares.
    public static final long ACCESS_TOKEN_EXPIRATION_MS = 1000L * 60 * 60;

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public boolean isTokenValid(String token) {
        try {
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** Conteúdo útil de um access token válido. {@code role} e {@code tokenVersion} são nulos em token antigo. */
    public record TokenData(String username, String role, Integer tokenVersion) {
    }

    /** Lê o token; vazio se a assinatura é inválida, se expirou ou se está malformado. */
    public java.util.Optional<TokenData> parse(String token) {
        try {
            Claims c = extractAllClaims(token);
            if (c.getExpiration() == null || c.getExpiration().before(new Date())) return java.util.Optional.empty();
            return java.util.Optional.of(new TokenData(c.getSubject(), c.get("role", String.class), c.get("tv", Integer.class)));
        } catch (Exception e) {
            return java.util.Optional.empty();
        }
    }

    /** Access token com o perfil (claim {@code role}) e a versão (claim {@code tv}) atuais do usuário. */
    public String generateToken(com.projeto.mapi.model.User user) {
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("role", user.getRole().name())
                .claim("tv", user.getTokenVersion())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRATION_MS))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
