package com.inacio.banking.security;

import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtService")
class JwtServiceTest {

    private static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres-para-hmac-sha256";

    private JwtService jwtService;
    private User user;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(properties(Duration.ofHours(1), Duration.ofDays(7)));
        user = User.builder()
                .id(UUID.randomUUID())
                .fullName("Maria Souza")
                .email("maria.souza@email.com")
                .document("39053344705")
                .passwordHash("hash")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    @Test
    @DisplayName("emite um token de acesso com subject, e-mail e perfil")
    void issuesAccessToken() {
        JwtService.IssuedToken issued = jwtService.issueAccessToken(user);

        Claims claims = jwtService.parse(issued.token(), TokenType.ACCESS).orElseThrow();

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_EMAIL, String.class)).isEqualTo(user.getEmail());
        assertThat(claims.get(JwtService.CLAIM_ROLE, String.class)).isEqualTo("USER");
        assertThat(claims.getId()).isEqualTo(issued.tokenId());
    }

    @Test
    @DisplayName("recusa um refresh token apresentado como token de acesso")
    void rejectsWrongTokenType() {
        JwtService.IssuedToken refresh = jwtService.issueRefreshToken(user);

        assertThat(jwtService.parse(refresh.token(), TokenType.ACCESS)).isEmpty();
        assertThat(jwtService.parse(refresh.token(), TokenType.REFRESH)).isPresent();
    }

    @Test
    @DisplayName("recusa token assinado com outro segredo")
    void rejectsForeignSignature() {
        JwtService other = new JwtService(
                properties(Duration.ofHours(1), Duration.ofDays(7), "outro-segredo-completamente-diferente-com-32+"));
        String foreignToken = other.issueAccessToken(user).token();

        assertThat(jwtService.parse(foreignToken, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("recusa token expirado")
    void rejectsExpiredToken() {
        JwtService shortLived = new JwtService(properties(Duration.ofMillis(-1), Duration.ofDays(7)));
        String expired = shortLived.issueAccessToken(user).token();

        assertThat(shortLived.parse(expired, TokenType.ACCESS)).isEmpty();
    }

    @Test
    @DisplayName("recusa entradas malformadas sem lancar excecao")
    void rejectsGarbage() {
        assertThat(jwtService.parse("nao-e-um-jwt", TokenType.ACCESS)).isEmpty();
        assertThat(jwtService.parse("", TokenType.ACCESS)).isEmpty();
        assertThat(jwtService.parse(null, TokenType.ACCESS)).isEqualTo(Optional.empty());
    }

    private static JwtProperties properties(Duration access, Duration refresh) {
        return properties(access, refresh, SECRET);
    }

    private static JwtProperties properties(Duration access, Duration refresh, String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        properties.setIssuer("banking-api");
        properties.setAccessTokenExpiration(access);
        properties.setRefreshTokenExpiration(refresh);
        return properties;
    }
}
