package com.inacio.banking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "RefreshTokenRequest", description = "Token de renovacao emitido no login")
public record RefreshTokenRequest(

        @Schema(description = "Refresh token JWT", example = "eyJhbGciOiJIUzI1NiJ9...", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O refresh token e obrigatorio")
        String refreshToken
) {
}
