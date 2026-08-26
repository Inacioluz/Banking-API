package com.inacio.banking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "LogoutRequest", description = "Encerramento de sessao")
public record LogoutRequest(

        @Schema(description = "Refresh token da sessao a encerrar. Ignorado quando allSessions e true.",
                example = "eyJhbGciOiJIUzI1NiJ9...", nullable = true)
        String refreshToken,

        @Schema(description = "Quando true, revoga todos os refresh tokens ativos do usuario",
                example = "false", defaultValue = "false")
        boolean allSessions
) {
}
