package com.inacio.banking.service;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.AccountType;
import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.domain.User;
import com.inacio.banking.dto.transaction.DepositRequest;
import com.inacio.banking.dto.transaction.TransactionResponse;
import com.inacio.banking.dto.transaction.TransferRequest;
import com.inacio.banking.dto.transaction.TransferResponse;
import com.inacio.banking.dto.transaction.WithdrawalRequest;
import com.inacio.banking.exception.BusinessRuleException;
import com.inacio.banking.exception.ForbiddenOperationException;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("TransactionService")
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;
    @Mock
    private TransactionRepository transactionRepository;
    @Mock
    private AccountCacheEvictor cacheEvictor;

    private AccountService accountService;
    private TransactionService transactionService;

    private User owner;
    private AuthenticatedUser currentUser;
    private Account account;

    @BeforeEach
    void setUp() {
        accountService = new AccountService(accountRepository, transactionRepository, null, null, null, cacheEvictor);
        transactionService = new TransactionService(accountRepository, transactionRepository, accountService, cacheEvictor);

        owner = user(Role.USER);
        currentUser = new AuthenticatedUser(owner);
        account = account(owner, new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.save(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Nested
    @DisplayName("deposito")
    class Deposit {

        @Test
        @DisplayName("credita o valor e registra o lancamento com o saldo resultante")
        void creditsAccount() {
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

            TransactionResponse response = transactionService.deposit(
                    account.getId(), currentUser, new DepositRequest(new BigDecimal("250.00"), "Salario"));

            assertThat(account.getBalance()).isEqualByComparingTo("1250.00");
            assertThat(response.type()).isEqualTo(TransactionType.DEPOSIT);
            assertThat(response.amount()).isEqualByComparingTo("250.00");
            assertThat(response.balanceAfter()).isEqualByComparingTo("1250.00");
            verify(cacheEvictor).evict(account);
        }

        @Test
        @DisplayName("recusa deposito em conta bloqueada")
        void rejectsBlockedAccount() {
            account.setStatus(AccountStatus.BLOCKED);
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> transactionService.deposit(
                    account.getId(), currentUser, new DepositRequest(new BigDecimal("10.00"), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_NOT_OPERATIONAL");

            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
        }

        @Test
        @DisplayName("recusa deposito de quem nao e titular nem admin")
        void rejectsNonOwner() {
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
            AuthenticatedUser intruder = new AuthenticatedUser(user(Role.USER));

            assertThatThrownBy(() -> transactionService.deposit(
                    account.getId(), intruder, new DepositRequest(new BigDecimal("10.00"), null)))
                    .isInstanceOf(ForbiddenOperationException.class);
        }

        @Test
        @DisplayName("falha quando a conta nao existe")
        void failsWhenAccountMissing() {
            UUID unknown = UUID.randomUUID();
            when(accountRepository.findByIdForUpdate(unknown)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.deposit(
                    unknown, currentUser, new DepositRequest(new BigDecimal("10.00"), null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("saque")
    class Withdrawal {

        @Test
        @DisplayName("debita o valor quando ha saldo")
        void debitsAccount() {
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

            TransactionResponse response = transactionService.withdraw(
                    account.getId(), currentUser, new WithdrawalRequest(new BigDecimal("400.00"), "Caixa"));

            assertThat(account.getBalance()).isEqualByComparingTo("600.00");
            assertThat(response.type()).isEqualTo(TransactionType.WITHDRAWAL);
            assertThat(response.balanceAfter()).isEqualByComparingTo("600.00");
        }

        @Test
        @DisplayName("permite sacar exatamente o saldo disponivel")
        void allowsExactBalance() {
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

            transactionService.withdraw(account.getId(), currentUser,
                    new WithdrawalRequest(new BigDecimal("1000.00"), null));

            assertThat(account.getBalance()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("recusa saque acima do saldo e nao altera a conta")
        void rejectsInsufficientFunds() {
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> transactionService.withdraw(
                    account.getId(), currentUser, new WithdrawalRequest(new BigDecimal("1000.01"), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_FUNDS");

            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
            verify(transactionRepository, never()).save(any(Transaction.class));
        }
    }

    @Nested
    @DisplayName("transferencia")
    class Transfer {

        @Test
        @DisplayName("move o valor entre as contas e gera as duas pernas do lancamento")
        void movesFunds() {
            Account target = account(user(Role.USER), new BigDecimal("50.00"), AccountStatus.ACTIVE);
            stubTransfer(target);

            TransferResponse response = transactionService.transfer(currentUser, new TransferRequest(
                    account.getId(), target.getAccountNumber(), new BigDecimal("300.00"), "Aluguel"));

            assertThat(account.getBalance()).isEqualByComparingTo("700.00");
            assertThat(target.getBalance()).isEqualByComparingTo("350.00");
            assertThat(response.sourceBalanceAfter()).isEqualByComparingTo("700.00");

            ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
            verify(transactionRepository, org.mockito.Mockito.times(2)).save(captor.capture());

            List<Transaction> legs = captor.getAllValues();
            assertThat(legs).extracting(Transaction::getType)
                    .containsExactly(TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN);
            assertThat(legs.get(0).getOperationId()).isEqualTo(legs.get(1).getOperationId());
            assertThat(legs.get(0).getOperationId()).isEqualTo(response.operationId());
        }

        @Test
        @DisplayName("recusa transferencia para a propria conta")
        void rejectsSameAccount() {
            when(accountRepository.findByAccountNumber(account.getAccountNumber())).thenReturn(Optional.of(account));

            assertThatThrownBy(() -> transactionService.transfer(currentUser, new TransferRequest(
                    account.getId(), account.getAccountNumber(), new BigDecimal("10.00"), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("SAME_ACCOUNT_TRANSFER");
        }

        @Test
        @DisplayName("recusa transferencia sem saldo e deixa as duas contas intactas")
        void rejectsInsufficientFunds() {
            Account target = account(user(Role.USER), new BigDecimal("50.00"), AccountStatus.ACTIVE);
            stubTransfer(target);

            assertThatThrownBy(() -> transactionService.transfer(currentUser, new TransferRequest(
                    account.getId(), target.getAccountNumber(), new BigDecimal("5000.00"), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("INSUFFICIENT_FUNDS");

            assertThat(account.getBalance()).isEqualByComparingTo("1000.00");
            assertThat(target.getBalance()).isEqualByComparingTo("50.00");
        }

        @Test
        @DisplayName("recusa transferencia para conta de destino bloqueada")
        void rejectsBlockedTarget() {
            Account target = account(user(Role.USER), new BigDecimal("50.00"), AccountStatus.BLOCKED);
            stubTransfer(target);

            assertThatThrownBy(() -> transactionService.transfer(currentUser, new TransferRequest(
                    account.getId(), target.getAccountNumber(), new BigDecimal("10.00"), null)))
                    .isInstanceOf(BusinessRuleException.class)
                    .extracting("errorCode").isEqualTo("ACCOUNT_NOT_OPERATIONAL");
        }

        @Test
        @DisplayName("falha quando a conta de destino nao existe")
        void failsWhenTargetMissing() {
            when(accountRepository.findByAccountNumber("0001-0000000000")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> transactionService.transfer(currentUser, new TransferRequest(
                    account.getId(), "0001-0000000000", new BigDecimal("10.00"), null)))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        private void stubTransfer(Account target) {
            when(accountRepository.findByAccountNumber(target.getAccountNumber())).thenReturn(Optional.of(target));
            when(accountRepository.findByIdForUpdate(account.getId())).thenReturn(Optional.of(account));
            when(accountRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        }
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

    private static Account account(User owner, BigDecimal balance, AccountStatus status) {
        return Account.builder()
                .id(UUID.randomUUID())
                .accountNumber("0001-" + (1_000_000_000L + (long) (Math.random() * 1_000_000_000L)))
                .owner(owner)
                .type(AccountType.CHECKING)
                .status(status)
                .balance(balance)
                .build();
    }
}
