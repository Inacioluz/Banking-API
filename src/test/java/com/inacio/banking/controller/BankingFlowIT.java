package com.inacio.banking.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.inacio.banking.AbstractIntegrationTest;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.AccountType;
import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.User;
import com.inacio.banking.dto.account.CreateAccountRequest;
import com.inacio.banking.dto.account.UpdateAccountStatusRequest;
import com.inacio.banking.dto.auth.LoginRequest;
import com.inacio.banking.dto.auth.LogoutRequest;
import com.inacio.banking.dto.auth.RefreshTokenRequest;
import com.inacio.banking.dto.auth.RegisterRequest;
import com.inacio.banking.dto.transaction.DepositRequest;
import com.inacio.banking.dto.transaction.TransferRequest;
import com.inacio.banking.dto.transaction.WithdrawalRequest;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fluxo completo da Banking API")
class BankingFlowIT extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        transactionRepository.deleteAll();
        accountRepository.deleteAll();
        userRepository.deleteAll();
        cacheManager.getCacheNames().stream()
                .map(cacheManager::getCache)
                .filter(java.util.Objects::nonNull)
                .forEach(org.springframework.cache.Cache::clear);
    }

    @Test
    @DisplayName("cadastro, abertura de conta, deposito, saque e extrato")
    void happyPath() throws Exception {
        String token = registerAndGetToken("ana.lima@email.com", "39053344705");

        // Abertura de conta com deposito inicial
        JsonNode account = json(mockMvc.perform(authorized(post("/api/v1/accounts"), token)
                        .content(body(new CreateAccountRequest(AccountType.CHECKING, new BigDecimal("500.00")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance").value(500.00))
                .andExpect(jsonPath("$.accountNumber").exists())
                .andReturn());

        String accountId = account.get("id").asText();

        // Deposito
        mockMvc.perform(authorized(post("/api/v1/accounts/{id}/deposits", accountId), token)
                        .content(body(new DepositRequest(new BigDecimal("250.50"), "Salario"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.balanceAfter").value(750.50));

        // Saque
        mockMvc.perform(authorized(post("/api/v1/accounts/{id}/withdrawals", accountId), token)
                        .content(body(new WithdrawalRequest(new BigDecimal("100.00"), "Caixa"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter").value(650.50));

        // Saldo — servido pelo cache Redis, precisa refletir a ultima movimentacao
        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/balance", accountId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(650.50))
                .andExpect(jsonPath("$.currency").value("BRL"));

        // Extrato: abertura + deposito + saque
        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/transactions", accountId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].type").value("WITHDRAWAL"));

        // Extrato filtrado por tipo
        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/transactions", accountId), token)
                        .param("type", "DEPOSIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("transferencia debita a origem e credita o destino")
    void transferBetweenAccounts() throws Exception {
        String senderToken = registerAndGetToken("bruno.dias@email.com", "39053344705");
        String receiverToken = registerAndGetToken("carla.melo@email.com", "52998224725");

        String sourceId = openAccount(senderToken, new BigDecimal("1000.00")).get("id").asText();
        JsonNode target = openAccount(receiverToken, new BigDecimal("0.00"));
        String targetNumber = target.get("accountNumber").asText();

        mockMvc.perform(authorized(post("/api/v1/transfers"), senderToken)
                        .content(body(new TransferRequest(UUID.fromString(sourceId), targetNumber,
                                new BigDecimal("300.00"), "Aluguel"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.sourceBalanceAfter").value(700.00))
                .andExpect(jsonPath("$.operationId").exists());

        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/balance", sourceId), senderToken))
                .andExpect(jsonPath("$.balance").value(700.00));

        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/balance", target.get("id").asText()), receiverToken))
                .andExpect(jsonPath("$.balance").value(300.00));

        // O destinatario ve a perna TRANSFER_IN no proprio extrato
        mockMvc.perform(authorized(get("/api/v1/accounts/{id}/transactions", target.get("id").asText()), receiverToken)
                        .param("type", "TRANSFER_IN"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].amount").value(300.00));
    }

    @Test
    @DisplayName("saque acima do saldo devolve 422 com codigo INSUFFICIENT_FUNDS")
    void rejectsOverdraft() throws Exception {
        String token = registerAndGetToken("diego.reis@email.com", "39053344705");
        String accountId = openAccount(token, new BigDecimal("100.00")).get("id").asText();

        mockMvc.perform(authorized(post("/api/v1/accounts/{id}/withdrawals", accountId), token)
                        .content(body(new WithdrawalRequest(new BigDecimal("100.01"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("INSUFFICIENT_FUNDS"))
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.path").exists());

        assertThat(accountRepository.findById(UUID.fromString(accountId)).orElseThrow().getBalance())
                .isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("valor invalido devolve 400 detalhando o campo rejeitado")
    void rejectsInvalidPayload() throws Exception {
        String token = registerAndGetToken("elisa.gomes@email.com", "39053344705");
        String accountId = openAccount(token, new BigDecimal("100.00")).get("id").asText();

        mockMvc.perform(authorized(post("/api/v1/accounts/{id}/deposits", accountId), token)
                        .content(body(new DepositRequest(new BigDecimal("-5.00"), null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.violations[0].field").value("amount"));
    }

    @Test
    @DisplayName("um cliente nao acessa a conta de outro")
    void rejectsForeignAccount() throws Exception {
        String ownerToken = registerAndGetToken("fabio.rocha@email.com", "39053344705");
        String intruderToken = registerAndGetToken("gisele.alves@email.com", "52998224725");
        String accountId = openAccount(ownerToken, new BigDecimal("100.00")).get("id").asText();

        mockMvc.perform(authorized(get("/api/v1/accounts/{id}", accountId), intruderToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("FORBIDDEN"));
    }

    @Test
    @DisplayName("rota protegida sem token devolve 401 no formato padrao de erro")
    void rejectsAnonymousAccess() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    @DisplayName("o refresh rotaciona o token e invalida o anterior")
    void rotatesRefreshToken() throws Exception {
        JsonNode session = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RegisterRequest("Lucas Prado", "lucas.prado@email.com",
                                "39053344705", "Senha@123"))))
                .andExpect(status().isCreated())
                .andReturn());

        String firstRefresh = session.get("refreshToken").asText();

        JsonNode renewed = json(mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RefreshTokenRequest(firstRefresh))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andReturn());

        assertThat(renewed.get("refreshToken").asText()).isNotEqualTo(firstRefresh);

        // O token ja consumido nao serve uma segunda vez.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RefreshTokenRequest(firstRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));

        // O novo token continua valido e o access dele abre uma rota protegida.
        mockMvc.perform(authorized(get("/api/v1/users/me"), renewed.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("lucas.prado@email.com"));
    }

    @Test
    @DisplayName("o logout revoga a sessao e o refresh deixa de funcionar")
    void logoutRevokesSession() throws Exception {
        JsonNode session = json(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RegisterRequest("Marta Vieira", "marta.vieira@email.com",
                                "39053344705", "Senha@123"))))
                .andExpect(status().isCreated())
                .andReturn());

        String accessToken = session.get("accessToken").asText();
        String refreshToken = session.get("refreshToken").asText();

        mockMvc.perform(authorized(post("/api/v1/auth/logout"), accessToken)
                        .content(body(new LogoutRequest(refreshToken, false))))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("conta bloqueada por admin recusa movimentacao")
    void blockedAccountRejectsMovement() throws Exception {
        String clientToken = registerAndGetToken("helena.dias@email.com", "39053344705");
        String accountId = openAccount(clientToken, new BigDecimal("100.00")).get("id").asText();
        String adminToken = createAdminAndGetToken();

        mockMvc.perform(authorized(patch("/api/v1/accounts/{id}/status", accountId), adminToken)
                        .content(body(new UpdateAccountStatusRequest(AccountStatus.BLOCKED))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("BLOCKED"));

        mockMvc.perform(authorized(post("/api/v1/accounts/{id}/deposits", accountId), clientToken)
                        .content(body(new DepositRequest(new BigDecimal("10.00"), null))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("ACCOUNT_NOT_OPERATIONAL"));
    }

    @Test
    @DisplayName("cliente comum nao acessa a rota administrativa")
    void rejectsNonAdminOnAdminRoute() throws Exception {
        String token = registerAndGetToken("igor.matos@email.com", "39053344705");

        mockMvc.perform(authorized(get("/api/v1/accounts/all"), token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("e-mail duplicado devolve 409")
    void rejectsDuplicateEmail() throws Exception {
        registerAndGetToken("julia.nunes@email.com", "39053344705");

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RegisterRequest("Julia Nunes", "julia.nunes@email.com",
                                "52998224725", "Senha@123"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("RESOURCE_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("a documentacao OpenAPI e servida e descreve o esquema de seguranca")
    void exposesOpenApiDocument() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Banking API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/v1/transfers'].post").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists());
    }

    // --- helpers ---

    private String registerAndGetToken(String email, String document) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new RegisterRequest("Cliente Teste", email, document, "Senha@123"))))
                .andExpect(status().isCreated())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private String createAdminAndGetToken() throws Exception {
        userRepository.save(User.builder()
                .fullName("Admin Teste")
                .email("admin.teste@banking.com")
                .document("11122233344")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role(Role.ADMIN)
                .enabled(true)
                .build());

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(new LoginRequest("admin.teste@banking.com", "Admin@123"))))
                .andExpect(status().isOk())
                .andReturn();
        return json(result).get("accessToken").asText();
    }

    private JsonNode openAccount(String token, BigDecimal initialDeposit) throws Exception {
        return json(mockMvc.perform(authorized(post("/api/v1/accounts"), token)
                        .content(body(new CreateAccountRequest(AccountType.CHECKING, initialDeposit))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private MockHttpServletRequestBuilder authorized(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON);
    }

    private String body(Object payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
