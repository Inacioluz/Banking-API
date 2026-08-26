package com.inacio.banking.controller;

import com.inacio.banking.dto.account.AccountResponse;
import com.inacio.banking.dto.account.BalanceResponse;
import com.inacio.banking.dto.account.CreateAccountRequest;
import com.inacio.banking.dto.account.UpdateAccountStatusRequest;
import com.inacio.banking.dto.common.PageResponse;
import com.inacio.banking.exception.ErrorResponse;
import com.inacio.banking.security.AuthenticatedUser;
import com.inacio.banking.security.CurrentUser;
import com.inacio.banking.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@Tag(name = "Contas")
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @Operation(
            summary = "Abre uma nova conta",
            description = """
                    Abre uma conta para o usuario autenticado. Um deposito inicial opcional
                    ja entra como o primeiro lancamento do extrato. Cada cliente pode ter no
                    maximo 5 contas.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conta aberta",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Limite de contas por cliente atingido",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 422,
                                      "error": "ACCOUNT_LIMIT_REACHED",
                                      "message": "Limite de 5 contas por cliente atingido",
                                      "path": "/api/v1/accounts"
                                    }
                                    """)))
    })
    @PostMapping
    public ResponseEntity<AccountResponse> openAccount(@CurrentUser AuthenticatedUser currentUser,
                                                       @Valid @RequestBody CreateAccountRequest request) {
        AccountResponse account = accountService.openAccount(currentUser, request);
        return ResponseEntity
                .created(UriComponentsBuilder.fromPath("/api/v1/accounts/{id}").buildAndExpand(account.id()).toUri())
                .body(account);
    }

    @Operation(
            summary = "Lista as contas do usuario autenticado",
            description = "Resposta servida pelo cache Redis (`accountsByOwner`, TTL de 5 minutos), invalidada a cada movimentacao.")
    @ApiResponse(responseCode = "200", description = "Contas do titular",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = AccountResponse.class))))
    @GetMapping
    public ResponseEntity<List<AccountResponse>> myAccounts(@CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountService.listMyAccounts(currentUser));
    }

    @Operation(
            summary = "Lista todas as contas da instituicao",
            description = "Rota administrativa, paginada. Exige perfil ADMIN.")
    @ApiResponse(responseCode = "200", description = "Pagina de contas",
            content = @Content(schema = @Schema(implementation = PageResponse.class)))
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/all")
    public ResponseEntity<PageResponse<AccountResponse>> allAccounts(
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(PageResponse.of(accountService.listAll(pageable), account -> account));
    }

    @Operation(
            summary = "Detalha uma conta",
            description = "Acessivel ao titular da conta ou a um administrador.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dados da conta",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{accountId}")
    public ResponseEntity<AccountResponse> getAccount(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountService.getAccountFor(accountId, currentUser));
    }

    @Operation(
            summary = "Consulta o saldo",
            description = "Resposta servida pelo cache Redis (`balances`, TTL de 30 segundos), invalidada a cada movimentacao.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo atual",
                    content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{accountId}/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @CurrentUser AuthenticatedUser currentUser) {
        return ResponseEntity.ok(accountService.getBalanceFor(accountId, currentUser));
    }

    @Operation(
            summary = "Altera a situacao de uma conta",
            description = "Bloqueia, reativa ou encerra uma conta. Rota administrativa.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Situacao alterada",
                    content = @Content(schema = @Schema(implementation = AccountResponse.class))),
            @ApiResponse(responseCode = "404", description = "Conta nao encontrada",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PreAuthorize("hasRole('ADMIN')")
    @PatchMapping("/{accountId}/status")
    public ResponseEntity<AccountResponse> updateStatus(
            @Parameter(description = "Identificador da conta", example = "4f6b1a90-8c2d-4b11-9f3e-77d5a1c2b3e4")
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountStatusRequest request) {
        return ResponseEntity.ok(accountService.updateStatus(accountId, request.status()));
    }
}
