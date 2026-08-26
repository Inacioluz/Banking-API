package com.inacio.banking.dto.transaction;

import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "TransactionResponse", description = "Lancamento registrado no extrato")
public record TransactionResponse(

        @Schema(description = "Identificador do lancamento", example = "1a2b3c4d-5e6f-4a7b-8c9d-0e1f2a3b4c5d")
        UUID id,

        @Schema(description = "Identificador da operacao — as duas pernas de uma transferencia compartilham o mesmo valor",
                example = "9f8e7d6c-5b4a-4392-8172-6f5e4d3c2b1a")
        UUID operationId,

        @Schema(description = "Conta movimentada", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
        UUID accountId,

        @Schema(description = "Numero da conta da contraparte, quando houver", example = "0001-9182736450", nullable = true)
        String counterpartyAccountNumber,

        @Schema(description = "Natureza do lancamento", example = "TRANSFER_OUT")
        TransactionType type,

        @Schema(description = "Valor movimentado", example = "75.50")
        BigDecimal amount,

        @Schema(description = "Saldo da conta apos o lancamento", example = "1175.25")
        BigDecimal balanceAfter,

        @Schema(description = "Descricao do lancamento", example = "Pagamento do aluguel", nullable = true)
        String description,

        @Schema(description = "Momento do lancamento", example = "2026-08-25T13:45:30Z")
        Instant createdAt
) implements Serializable {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getOperationId(),
                transaction.getAccount().getId(),
                transaction.getCounterpartyAccount() == null ? null : transaction.getCounterpartyAccount().getAccountNumber(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getCreatedAt()
        );
    }
}
