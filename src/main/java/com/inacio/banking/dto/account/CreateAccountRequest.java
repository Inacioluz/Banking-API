package com.inacio.banking.dto.account;

import com.inacio.banking.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

@Schema(name = "CreateAccountRequest", description = "Dados para abertura de conta")
public record CreateAccountRequest(

        @Schema(description = "Tipo da conta", example = "CHECKING", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"CHECKING", "SAVINGS"})
        @NotNull(message = "O tipo da conta e obrigatorio")
        AccountType type,

        @Schema(description = "Deposito inicial opcional", example = "500.00", defaultValue = "0.00")
        @DecimalMin(value = "0.00", message = "O deposito inicial nao pode ser negativo")
        @Digits(integer = 17, fraction = 2, message = "Valor monetario invalido")
        BigDecimal initialDeposit
) {

    public BigDecimal initialDepositOrZero() {
        return initialDeposit == null ? BigDecimal.ZERO : initialDeposit;
    }
}
