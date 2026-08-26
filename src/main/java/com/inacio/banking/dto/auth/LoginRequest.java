package com.inacio.banking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

@Schema(name = "LoginRequest", description = "Credenciais de acesso")
public record LoginRequest(

        @Schema(description = "E-mail cadastrado", example = "maria.souza@email.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "E-mail invalido")
        String email,

        @Schema(description = "Senha do usuario", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A senha e obrigatoria")
        String password
) {
}
