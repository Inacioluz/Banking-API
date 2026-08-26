package com.inacio.banking.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "ErrorResponse", description = "Formato unico de erro devolvido pela API")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(

        @Schema(description = "Momento em que o erro ocorreu", example = "2026-08-25T13:45:30Z")
        Instant timestamp,

        @Schema(description = "Codigo HTTP", example = "422")
        int status,

        @Schema(description = "Codigo de erro estavel para tratamento pelo cliente", example = "INSUFFICIENT_FUNDS")
        String error,

        @Schema(description = "Mensagem legivel", example = "Saldo insuficiente para concluir a operacao")
        String message,

        @Schema(description = "Caminho da requisicao", example = "/api/v1/accounts/4f6b1a90.../withdrawals")
        String path,

        @Schema(description = "Detalhamento por campo em erros de validacao", nullable = true)
        List<FieldViolation> violations
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, List<FieldViolation> violations) {
        return new ErrorResponse(Instant.now(), status, error, message, path,
                violations == null || violations.isEmpty() ? null : violations);
    }

    @Schema(name = "FieldViolation", description = "Violacao de validacao em um campo especifico")
    public record FieldViolation(

            @Schema(description = "Campo rejeitado", example = "amount")
            String field,

            @Schema(description = "Motivo da rejeicao", example = "O valor deve ser maior que zero")
            String message,

            @Schema(description = "Valor recebido", example = "-10", nullable = true)
            Object rejectedValue
    ) {
    }
}
