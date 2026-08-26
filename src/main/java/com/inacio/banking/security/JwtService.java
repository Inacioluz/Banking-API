package com.inacio.banking.security;

import com.inacio.banking.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Emissao e verificacao dos JWTs da aplicacao.
 */
@Slf4j
@Service
public class JwtService {

    static final String CLAIM_TYPE = "typ";
    static final String CLAIM_EMAIL = "email";
    static final String CLAIM_ROLE = "role";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public IssuedToken issueAccessToken(User user) {
        return issue(user, TokenType.ACCESS, properties.getAccessTokenExpiration());
    }

    public IssuedToken issueRefreshToken(User user) {
        return issue(user, TokenType.REFRESH, properties.getRefreshTokenExpiration());
    }

    private IssuedToken issue(User user, TokenType type, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        String tokenId = UUID.randomUUID().toString();

        String token = Jwts.builder()
                .id(tokenId)
                .subject(user.getId().toString())
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .claim(CLAIM_TYPE, type.name())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLE, user.getRole().name())
                .signWith(signingKey)
                .compact();

        return new IssuedToken(token, tokenId, expiresAt, ttl);
    }

    /**
     * Valida assinatura, expiracao e tipo esperado. Retorna vazio quando o token
     * e invalido por qualquer motivo — a causa fica apenas no log.
     */
    public Optional<Claims> parse(String token, TokenType expectedType) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.getIssuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!expectedType.name().equals(claims.get(CLAIM_TYPE, String.class))) {
                log.debug("Token com tipo inesperado: esperado {}", expectedType);
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("Token rejeitado: {}", ex.getMessage());
            return Optional.empty();
        }
    }

    public long accessTokenExpirationSeconds() {
        return properties.getAccessTokenExpiration().toSeconds();
    }

    public Duration refreshTokenExpiration() {
        return properties.getRefreshTokenExpiration();
    }

    /**
     * Token emitido junto com os metadados necessarios para armazenamento e resposta.
     */
    public record IssuedToken(String token, String tokenId, Instant expiresAt, Duration ttl) {
    }
}
