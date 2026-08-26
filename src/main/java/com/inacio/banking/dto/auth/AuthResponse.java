package com.inacio.banking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "AuthResponse", description = "Par de tokens emitido pela autenticacao")
public record AuthResponse(

        @Schema(description = "Token de acesso JWT usado no header Authorization", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "Token usado para renovar o acesso sem novo login", example = "eyJhbGciOiJIUzI1NiJ9...")
        String refreshToken,

        @Schema(description = "Tipo do token", example = "Bearer")
        String tokenType,

        @Schema(description = "Validade do token de acesso em segundos", example = "3600")
        long expiresIn,

        @Schema(description = "Dados do usuario autenticado")
        UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }
}
