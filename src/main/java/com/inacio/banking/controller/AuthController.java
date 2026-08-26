package com.inacio.banking.controller;

import com.inacio.banking.dto.auth.AuthResponse;
import com.inacio.banking.dto.auth.LoginRequest;
import com.inacio.banking.dto.auth.LogoutRequest;
import com.inacio.banking.dto.auth.RefreshTokenRequest;
import com.inacio.banking.dto.auth.RegisterRequest;
import com.inacio.banking.exception.ErrorResponse;
import com.inacio.banking.security.AuthenticatedUser;
import com.inacio.banking.security.CurrentUser;
import com.inacio.banking.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Autenticacao")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(
            summary = "Cadastra um novo cliente",
            description = """
                    Cria o cadastro e ja devolve o par de tokens, dispensando um login logo em seguida.
                    A senha e armazenada com BCrypt (força 12) e nunca retorna em nenhuma resposta.
                    """,
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Cadastro criado",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(name = "Validacao", value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 400,
                                      "error": "VALIDATION_ERROR",
                                      "message": "Um ou mais campos estao invalidos",
                                      "path": "/api/v1/auth/register",
                                      "violations": [
                                        { "field": "email", "message": "E-mail invalido", "rejectedValue": "maria@" }
                                      ]
                                    }
                                    """))),
            @ApiResponse(responseCode = "409", description = "E-mail ou documento ja cadastrado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 409,
                                      "error": "RESOURCE_ALREADY_EXISTS",
                                      "message": "Ja existe um cadastro com o e-mail informado",
                                      "path": "/api/v1/auth/register"
                                    }
                                    """)))
    })
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @Operation(
            summary = "Autentica e emite os tokens",
            description = """
                    Devolve um `accessToken` de curta duracao e um `refreshToken` de longa duracao.
                    Envie o token de acesso nas rotas protegidas no header `Authorization: Bearer <token>`.
                    """,
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Autenticado",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "400", description = "Campos invalidos",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciais invalidas",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 401,
                                      "error": "INVALID_CREDENTIALS",
                                      "message": "E-mail ou senha invalidos",
                                      "path": "/api/v1/auth/login"
                                    }
                                    """))),
            @ApiResponse(responseCode = "403", description = "Cadastro desativado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 403,
                                      "error": "ACCOUNT_DISABLED",
                                      "message": "Cadastro desativado, procure o suporte",
                                      "path": "/api/v1/auth/login"
                                    }
                                    """)))
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @Operation(
            summary = "Renova o par de tokens",
            description = """
                    Troca um refresh token valido por um novo par. O token apresentado e revogado
                    no mesmo ato (rotacao) — reutiliza-lo resulta em 401.
                    """,
            security = {})
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Tokens renovados",
                    content = @Content(schema = @Schema(implementation = AuthResponse.class))),
            @ApiResponse(responseCode = "401", description = "Refresh token invalido, expirado ou ja utilizado",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = """
                                    {
                                      "timestamp": "2026-08-25T13:45:30Z",
                                      "status": 401,
                                      "error": "INVALID_REFRESH_TOKEN",
                                      "message": "Refresh token invalido, expirado ou ja utilizado",
                                      "path": "/api/v1/auth/refresh"
                                    }
                                    """)))
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @Operation(
            summary = "Encerra a sessao",
            description = "Revoga o refresh token informado ou, com `allSessions: true`, todas as sessoes do usuario.")
    @ApiResponse(responseCode = "204", description = "Sessao encerrada")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@CurrentUser AuthenticatedUser currentUser,
                                       @RequestBody(required = false) LogoutRequest request) {
        LogoutRequest payload = request == null ? new LogoutRequest(null, false) : request;
        authService.logout(currentUser, payload.refreshToken(), payload.allSessions());
        return ResponseEntity.noContent().build();
    }
}
