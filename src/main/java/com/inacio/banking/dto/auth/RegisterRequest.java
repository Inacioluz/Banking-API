package com.inacio.banking.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(name = "RegisterRequest", description = "Dados para cadastro de um novo cliente")
public record RegisterRequest(

        @Schema(description = "Nome completo do cliente", example = "Maria Souza", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O nome completo e obrigatorio")
        @Size(min = 3, max = 120, message = "O nome deve ter entre 3 e 120 caracteres")
        String fullName,

        @Schema(description = "E-mail unico usado no login", example = "maria.souza@email.com", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O e-mail e obrigatorio")
        @Email(message = "E-mail invalido")
        @Size(max = 180, message = "O e-mail deve ter no maximo 180 caracteres")
        String email,

        @Schema(description = "CPF do cliente, somente digitos", example = "39053344705", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "O documento e obrigatorio")
        @Pattern(regexp = "\\d{11}", message = "O documento deve conter 11 digitos")
        String document,

        @Schema(description = "Senha com no minimo 8 caracteres, contendo letra e numero", example = "Senha@123", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A senha e obrigatoria")
        @Size(min = 8, max = 64, message = "A senha deve ter entre 8 e 64 caracteres")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "A senha deve conter ao menos uma letra e um numero")
        String password
) {
}
