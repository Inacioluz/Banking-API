package com.inacio.banking.dto.transaction;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TransferResponse", description = "Resultado consolidado de uma transferencia")
public record TransferResponse(

        @Schema(description = "Identificador da operacao", example = "9f8e7d6c-5b4a-4392-8172-6f5e4d3c2b1a")
        UUID operationId,

        @Schema(description = "Numero da conta de origem", example = "0001-4837291056")
        String sourceAccountNumber,

        @Schema(description = "Numero da conta de destino", example = "0001-9182736450")
        String targetAccountNumber,

        @Schema(description = "Valor transferido", example = "75.50")
        BigDecimal amount,

        @Schema(description = "Saldo da conta de origem apos a transferencia", example = "1175.25")
        BigDecimal sourceBalanceAfter,

        @Schema(description = "Momento da transferencia", example = "2026-08-25T13:45:30Z")
        Instant executedAt
) {
}
