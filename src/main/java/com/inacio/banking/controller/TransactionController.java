package com.inacio.banking.controller;

import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.dto.common.PageResponse;
import com.inacio.banking.dto.transaction.DepositRequest;
import com.inacio.banking.dto.transaction.TransactionResponse;
import com.inacio.banking.dto.transaction.TransferRequest;
import com.inacio.banking.dto.transaction.TransferResponse;
import com.inacio.banking.dto.transaction.WithdrawalRequest;
import com.inacio.banking.exception.ErrorResponse;
import com.inacio.banking.security.AuthenticatedUser;
import com.inacio.banking.security.CurrentUser;
import com.inacio.banking.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Function;

@Tag(name = "Transacoes")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TransactionController {

    private final TransactionService transactionService;

    @Operation(
            summary = "Deposita em uma conta",
            description = """
                    Credita o valor informado e registra o lancamento no extrato.
                    A conta precisa estar com situacao `ACTIVE`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Deposito efetuado",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Valor invalido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Conta bloqueada ou encerrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 422,
                                      "error": "ACCOUNT_NOT_OPERATIONAL",
                                      "message": "A conta 0001-4837291056 nao esta ativa e nao aceita movimentacoes",
                                      "path": "/api/v1/accounts/4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4/deposits"
                                    }
                                    """)))
    })
    @PostMapping("/accounts/{accountId}/deposits")
    public ResponseEntity<TransactionResponse> deposit(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody DepositRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.deposit(accountId, currentUser, request));
    }

    @Operation(
            summary = "Saca de uma conta",
            description = "Debita o valor informado. Recusa a operacao quando o saldo e insuficiente.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saque efetuado",
                    content = @Content(schema = @Schema(implementation = TransactionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente ou conta nao operacional",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Saldo insuficiente", value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 422,
                                      "error": "INSUFFICIENT_FUNDS",
                                      "message": "Saldo insuficiente para concluir a operacao",
                                      "path": "/api/v1/accounts/4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4/withdrawals"
                                    }
                                    """)))
    })
    @PostMapping("/accounts/{accountId}/withdrawals")
    public ResponseEntity<TransactionResponse> withdraw(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @CurrentUser AuthenticatedUser currentUser,
            @Valid @RequestBody WithdrawalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.withdraw(accountId, currentUser, request));
    }

    @Operation(
            summary = "Transfere entre contas",
            description = """
                    Debita a conta de origem e credita a de destino em uma unica transacao de banco.
                    As duas contas sao travadas em ordem deterministica, de modo que transferencias
                    cruzadas simultaneas nao geram deadlock. Gera dois lancamentos correlacionados
                    pelo mesmo `operationId`: `TRANSFER_OUT` na origem e `TRANSFER_IN` no destino.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Transferencia efetuada",
                    content = @Content(schema = @Schema(implementation = TransferResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta de origem ou destino nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Saldo insuficiente, conta nao operacional ou origem igual ao destino",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Saldo insuficiente", value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 422,
                                      "error": "INSUFFICIENT_FUNDS",
                                      "message": "Saldo insuficiente para concluir a operacao",
                                      "path": "/api/v1/transfers"
                                    }
                                    """)))
    })
    @PostMapping("/transfers")
    public ResponseEntity<TransferResponse> transfer(@CurrentUser AuthenticatedUser currentUser,
                                                     @Valid @RequestBody TransferRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.transfer(currentUser, request));
    }

    @Operation(
            summary = "Extrato da conta",
            description = """
                    Lista os lancamentos da conta, do mais recente para o mais antigo,
                    com filtros opcionais por tipo e periodo. Datas no formato ISO-8601 UTC
                    (ex.: `2026-08-01T00:00:00Z`).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pagina do extrato",
                    content = @Content(schema = @Schema(implementation = PageResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<PageResponse<TransactionResponse>> statement(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @CurrentUser AuthenticatedUser currentUser,
            @Parameter(description = "Filtra por tipo de lancamento", example = "TRANSFER_OUT")
            @RequestParam(required = false) TransactionType type,
            @Parameter(description = "Inicio do periodo (ISO-8601 UTC)", example = "2026-08-01T00:00:00Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @Parameter(description = "Fim do periodo (ISO-8601 UTC)", example = "2026-08-31T23:59:59Z")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(PageResponse.of(
                transactionService.statement(accountId, currentUser, type, from, to, pageable),
                Function.identity()));
    }
}
