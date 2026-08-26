package com.inacio.banking.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(name = "WithdrawalRequest", description = "Saque em conta")
public record WithdrawalRequest(

        @Schema(description = "Valor do saque", example = "100.00", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "Valor monetario invalido")
        BigDecimal amount,

        @Schema(description = "Descricao livre do lancamento", example = "Saque no caixa eletronico")
        @Size(max = 255, message = "A descricao deve ter no maximo 255 caracteres")
        String description
) {
}
