package com.inacio.banking.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

@Schema(name = "TransferRequest", description = "Transferencia entre contas da instituicao")
public record TransferRequest(

        @Schema(description = "Conta de origem (precisa pertencer ao usuario autenticado)",
                example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "A conta de origem e obrigatoria")
        UUID sourceAccountId,

        @Schema(description = "Numero da conta de destino", example = "0001-9182736450", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "A conta de destino e obrigatoria")
        String targetAccountNumber,

        @Schema(description = "Valor transferido", example = "75.50", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull(message = "O valor e obrigatorio")
        @DecimalMin(value = "0.01", message = "O valor deve ser maior que zero")
        @Digits(integer = 17, fraction = 2, message = "Valor monetario invalido")
        BigDecimal amount,

        @Schema(description = "Descricao livre do lancamento", example = "Pagamento do aluguel")
        @Size(max = 255, message = "A descricao deve ter no maximo 255 caracteres")
        String description
) {
}
