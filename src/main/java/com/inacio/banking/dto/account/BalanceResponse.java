package com.inacio.banking.dto.account;

import com.inacio.banking.domain.Account;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(name = "BalanceResponse", description = "Consulta de saldo (resposta servida por cache Redis)")
public record BalanceResponse(

        @Schema(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
        UUID accountId,

        @Schema(description = "Numero da conta", example = "0001-4837291056")
        String accountNumber,

        @Schema(description = "Saldo disponivel", example = "1250.75")
        BigDecimal balance,

        @Schema(description = "Moeda no padrao ISO-4217", example = "BRL")
        String currency,

        @Schema(description = "Momento da apuracao do saldo", example = "2026-08-25T13:45:30Z")
        Instant retrievedAt
) implements Serializable {

    public static BalanceResponse from(Account account) {
        return new BalanceResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getBalance(),
                "BRL",
                Instant.now()
        );
    }
}
