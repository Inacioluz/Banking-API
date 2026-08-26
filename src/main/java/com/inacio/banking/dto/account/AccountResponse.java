package com.inacio.banking.dto.account;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.AccountType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "AccountResponse", description = "Representacao de uma conta bancaria")
public record AccountResponse(

        @Schema(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
        UUID id,

        @Schema(description = "Numero da conta", example = "0001-4837291056")
        String accountNumber,

        @Schema(description = "Identificador do titular", example = "8e9f1c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID ownerId,

        @Schema(description = "Nome do titular", example = "Maria Souza")
        String ownerName,

        @Schema(description = "Tipo da conta", example = "CHECKING")
        AccountType type,

        @Schema(description = "Situacao da conta", example = "ACTIVE")
        AccountStatus status,

        @Schema(description = "Saldo atual", example = "1250.75")
        BigDecimal balance,

        @Schema(description = "Data de abertura", example = "2026-08-25T13:45:30Z")
        Instant createdAt
) implements Serializable {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getOwner().getId(),
                account.getOwner().getFullName(),
                account.getType(),
                account.getStatus(),
                account.getBalance(),
                account.getCreatedAt()
        );
    }
}
