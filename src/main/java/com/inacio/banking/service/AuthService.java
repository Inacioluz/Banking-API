package com.inacio.banking.service;

import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.User;
import com.inacio.banking.dto.auth.AuthResponse;
import com.inacio.banking.dto.auth.LoginRequest;
import com.inacio.banking.dto.auth.RefreshTokenRequest;
import com.inacio.banking.dto.auth.RegisterRequest;
import com.inacio.banking.dto.auth.UserResponse;
import com.inacio.banking.exception.DuplicateResourceException;
import com.inacio.banking.exception.InvalidTokenException;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.UserRepository;
import com.inacio.banking.security.AuthenticatedUser;
import com.inacio.banking.security.JwtService;
import com.inacio.banking.security.RefreshTokenStore;
import com.inacio.banking.security.TokenType;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenStore refreshTokenStore;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new DuplicateResourceException("Ja existe um cadastro com o e-mail informado");
        }
        if (userRepository.existsByDocument(request.document())) {
            throw new DuplicateResourceException("Ja existe um cadastro com o documento informado");
        }

        User user = userRepository.save(User.builder()
                .fullName(request.fullName().trim())
                .email(email)
                .document(request.document())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.USER)
                .enabled(true)
                .build());

        log.info("Novo cadastro criado: {}", user.getId());
        return issueTokens(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email().trim().toLowerCase(), request.password()));

        AuthenticatedUser principal = (AuthenticatedUser) authentication.getPrincipal();
        User user = principal.getUser();

        log.info("Login efetuado: {}", user.getId());
        return issueTokens(user);
    }

    /**
     * Renova o par de tokens. O refresh apresentado e revogado no mesmo ato
     * (rotacao), de forma que reutiliza-lo falha.
     */
    @Transactional(readOnly = true)
    public AuthResponse refresh(RefreshTokenRequest request) {
        Claims claims = jwtService.parse(request.refreshToken(), TokenType.REFRESH)
                .orElseThrow(InvalidTokenException::new);

        UUID userId = UUID.fromString(claims.getSubject());
        String tokenId = claims.getId();

        if (!refreshTokenStore.isActive(tokenId, userId)) {
            log.warn("Tentativa de uso de refresh token revogado para o usuario {}", userId);
            throw new InvalidTokenException();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ResourceNotFoundException.user(userId));

        refreshTokenStore.revoke(tokenId, userId);
        return issueTokens(user);
    }

    /** Encerra a sessao atual ou, opcionalmente, todas as sessoes do usuario. */
    public void logout(AuthenticatedUser currentUser, String refreshToken, boolean allSessions) {
        if (allSessions) {
            refreshTokenStore.revokeAll(currentUser.getId());
            log.info("Todas as sessoes do usuario {} foram encerradas", currentUser.getId());
            return;
        }
        jwtService.parse(refreshToken, TokenType.REFRESH)
                .ifPresent(claims -> refreshTokenStore.revoke(claims.getId(), currentUser.getId()));
    }

    private AuthResponse issueTokens(User user) {
        JwtService.IssuedToken accessToken = jwtService.issueAccessToken(user);
        JwtService.IssuedToken refreshToken = jwtService.issueRefreshToken(user);

        refreshTokenStore.store(refreshToken.tokenId(), user.getId(), refreshToken.ttl());

        return AuthResponse.of(
                accessToken.token(),
                refreshToken.token(),
                jwtService.accessTokenExpirationSeconds(),
                UserResponse.from(user));
    }
}
