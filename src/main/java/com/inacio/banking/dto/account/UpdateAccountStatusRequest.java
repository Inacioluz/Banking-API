package com.inacio.banking.dto.account;

import com.inacio.banking.domain.AccountStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "UpdateAccountStatusRequest", description = "Alteracao de situacao da conta (restrito a ADMIN)")
public record UpdateAccountStatusRequest(

        @Schema(description = "Nova situacao", example = "BLOCKED", requiredMode = Schema.RequiredMode.REQUIRED,
                allowableValues = {"ACTIVE", "BLOCKED", "CLOSED"})
        @NotNull(message = "A situacao e obrigatoria")
        AccountStatus status
) {
}
