package com.inacio.banking.service;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.AccountType;
import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.domain.User;
import com.inacio.banking.dto.account.AccountResponse;
import com.inacio.banking.dto.account.CreateAccountRequest;
import com.inacio.banking.exception.BusinessRuleException;
import com.inacio.banking.exception.ForbiddenOperationException;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.repository.UserRepository;
import com.inacio.banking.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AccountQueryService accountQueryService;
    @Mock
    private AccountNumberGenerator accountNumberGenerator;
    @Mock
    private AccountCacheEvictor cacheEvictor;

    @InjectMocks
    private AccountService accountService;

    private User owner;
    private AuthenticatedUser currentUser;

    @BeforeEach
    void setUp() {
        owner = user(Role.USER);
        currentUser = new AuthenticatedUser(owner);

        when(userRepository.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(accountNumberGenerator.generate()).thenReturn("0001-1234567890");
        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("abre conta ativa com saldo zero quando nao ha deposito inicial")
    void opensEmptyAccount() {
        when(accountRepository.countByOwnerId(owner.getId())).thenReturn(0L);

        AccountResponse response = accountService.openAccount(currentUser,
                new CreateAccountRequest(AccountType.CHECKING, null));

        assertThat(response.balance()).isEqualByComparingTo("0.00");
        assertThat(response.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(response.accountNumber()).isEqualTo("0001-1234567890");
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    @DisplayName("registra o deposito de abertura no extrato")
    void recordsInitialDeposit() {
        when(accountRepository.countByOwnerId(owner.getId())).thenReturn(0L);

        AccountResponse response = accountService.openAccount(currentUser,
                new CreateAccountRequest(AccountType.SAVINGS, new BigDecimal("500.00")));

        assertThat(response.balance()).isEqualByComparingTo("500.00");

        var captor = org.mockito.ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(captor.capture());
        assertThat(captor.getValue().getType()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(captor.getValue().getBalanceAfter()).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("recusa a abertura quando o cliente atingiu o limite de contas")
    void rejectsAboveAccountLimit() {
        when(accountRepository.countByOwnerId(owner.getId()))
                .thenReturn((long) AccountService.MAX_ACCOUNTS_PER_USER);

        assertThatThrownBy(() -> accountService.openAccount(currentUser,
                new CreateAccountRequest(AccountType.CHECKING, null)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("errorCode").isEqualTo("ACCOUNT_LIMIT_REACHED");

        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    @DisplayName("titular acessa a propria conta; um terceiro recebe 403")
    void enforcesOwnership() {
        UUID ownerId = owner.getId();

        assertThatCode(() -> accountService.ensureCanAccess(ownerId, currentUser))
                .doesNotThrowAnyException();

        AuthenticatedUser intruder = new AuthenticatedUser(user(Role.USER));
        assertThatThrownBy(() -> accountService.ensureCanAccess(ownerId, intruder))
                .isInstanceOf(ForbiddenOperationException.class);
    }

    @Test
    @DisplayName("administrador acessa a conta de qualquer cliente")
    void adminBypassesOwnership() {
        AuthenticatedUser admin = new AuthenticatedUser(user(Role.ADMIN));

        assertThatCode(() -> accountService.ensureCanAccess(owner.getId(), admin))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("alterar a situacao da conta invalida o cache")
    void statusChangeEvictsCache() {
        Account account = Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("0001-1234567890")
                .owner(owner)
                .type(AccountType.CHECKING)
                .status(AccountStatus.ACTIVE)
                .balance(new BigDecimal("10.00"))
                .build();
        when(accountRepository.findById(account.getId())).thenReturn(Optional.of(account));
        when(accountRepository.save(any(Account.class))).thenAnswer(i -> i.getArgument(0));

        AccountResponse response = accountService.updateStatus(account.getId(), AccountStatus.BLOCKED);

        assertThat(response.status()).isEqualTo(AccountStatus.BLOCKED);
        verify(cacheEvictor).evict(account);
    }

    private static User user(Role role) {
        return User.builder()
                .id(UUID.randomUUID())
                .fullName("Cliente Teste")
                .email("cliente" + UUID.randomUUID() + "@email.com")
                .document("39053344705")
                .passwordHash("hash")
                .role(role)
                .enabled(true)
                .build();
    }
}
