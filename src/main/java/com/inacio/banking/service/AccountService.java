package com.inacio.banking.service;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.domain.User;
import com.inacio.banking.dto.account.AccountResponse;
import com.inacio.banking.dto.account.BalanceResponse;
import com.inacio.banking.dto.account.CreateAccountRequest;
import com.inacio.banking.exception.BusinessRuleException;
import com.inacio.banking.exception.ForbiddenOperationException;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.repository.UserRepository;
import com.inacio.banking.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountService {

    /** Numero maximo de contas por cliente. */
    public static final int MAX_ACCOUNTS_PER_USER = 5;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final AccountQueryService accountQueryService;
    private final AccountNumberGenerator accountNumberGenerator;
    private final AccountCacheEvictor cacheEvictor;

    @Transactional
    public AccountResponse openAccount(AuthenticatedUser currentUser, CreateAccountRequest request) {
        User owner = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> ResourceNotFoundException.user(currentUser.getId()));

        if (accountRepository.countByOwnerId(owner.getId()) >= MAX_ACCOUNTS_PER_USER) {
            throw BusinessRuleException.accountLimitReached(MAX_ACCOUNTS_PER_USER);
        }

        BigDecimal initialDeposit = normalize(request.initialDepositOrZero());

        Account account = accountRepository.saveAndFlush(Account.builder()
                .accountNumber(accountNumberGenerator.generate())
                .owner(owner)
                .type(request.type())
                .status(AccountStatus.ACTIVE)
                .balance(initialDeposit)
                .build());

        if (initialDeposit.signum() > 0) {
            transactionRepository.save(Transaction.builder()
                    .operationId(UUID.randomUUID())
                    .account(account)
                    .type(TransactionType.DEPOSIT)
                    .amount(initialDeposit)
                    .balanceAfter(account.getBalance())
                    .description("Deposito de abertura de conta")
                    .build());
        }

        cacheEvictor.evict(account);
        log.info("Conta {} aberta para o usuario {}", account.getAccountNumber(), owner.getId());
        return AccountResponse.from(account);
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccountFor(UUID accountId, AuthenticatedUser currentUser) {
        AccountResponse account = accountQueryService.getById(accountId);
        ensureCanAccess(account.ownerId(), currentUser);
        return account;
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalanceFor(UUID accountId, AuthenticatedUser currentUser) {
        ensureCanAccess(accountQueryService.getById(accountId).ownerId(), currentUser);
        return accountQueryService.getBalance(accountId);
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> listMyAccounts(AuthenticatedUser currentUser) {
        return accountQueryService.listByOwner(currentUser.getId());
    }

    /** Listagem administrativa de todas as contas da instituicao. */
    @Transactional(readOnly = true)
    public Page<AccountResponse> listAll(Pageable pageable) {
        return accountRepository.findAll(pageable).map(AccountResponse::from);
    }

    @Transactional
    public AccountResponse updateStatus(UUID accountId, AccountStatus status) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));

        account.setStatus(status);
        Account saved = accountRepository.save(account);

        cacheEvictor.evict(saved);
        log.info("Conta {} teve a situacao alterada para {}", saved.getAccountNumber(), status);
        return AccountResponse.from(saved);
    }

    /**
     * Autoriza titular ou administrador; lanca 403 nos demais casos.
     */
    public void ensureCanAccess(UUID ownerId, AuthenticatedUser currentUser) {
        if (!currentUser.isAdmin() && !ownerId.equals(currentUser.getId())) {
            throw ForbiddenOperationException.notAccountOwner();
        }
    }

    private BigDecimal normalize(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
