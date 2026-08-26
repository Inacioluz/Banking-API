package com.inacio.banking.service;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.dto.transaction.DepositRequest;
import com.inacio.banking.dto.transaction.TransactionResponse;
import com.inacio.banking.dto.transaction.TransferRequest;
import com.inacio.banking.dto.transaction.TransferResponse;
import com.inacio.banking.dto.transaction.WithdrawalRequest;
import com.inacio.banking.exception.BusinessRuleException;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.repository.TransactionSpecifications;
import com.inacio.banking.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Movimentacoes financeiras. Toda alteracao de saldo acontece dentro de uma
 * transacao com lock pessimista na linha da conta, para que operacoes
 * concorrentes sobre a mesma conta sejam serializadas pelo banco.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountService accountService;
    private final AccountCacheEvictor cacheEvictor;

    @Transactional
    public TransactionResponse deposit(UUID accountId, AuthenticatedUser currentUser, DepositRequest request) {
        Account account = lockAccount(accountId);
        accountService.ensureCanAccess(account.getOwner().getId(), currentUser);
        ensureOperational(account);

        BigDecimal amount = normalize(request.amount());
        account.credit(amount);
        accountRepository.save(account);

        Transaction transaction = record(UUID.randomUUID(), account, null, TransactionType.DEPOSIT,
                amount, request.description());

        cacheEvictor.evict(account);
        log.info("Deposito de {} na conta {}", amount, account.getAccountNumber());
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransactionResponse withdraw(UUID accountId, AuthenticatedUser currentUser, WithdrawalRequest request) {
        Account account = lockAccount(accountId);
        accountService.ensureCanAccess(account.getOwner().getId(), currentUser);
        ensureOperational(account);

        BigDecimal amount = normalize(request.amount());
        if (!account.hasSufficientFunds(amount)) {
            throw BusinessRuleException.insufficientFunds();
        }

        account.debit(amount);
        accountRepository.save(account);

        Transaction transaction = record(UUID.randomUUID(), account, null, TransactionType.WITHDRAWAL,
                amount, request.description());

        cacheEvictor.evict(account);
        log.info("Saque de {} na conta {}", amount, account.getAccountNumber());
        return TransactionResponse.from(transaction);
    }

    @Transactional
    public TransferResponse transfer(AuthenticatedUser currentUser, TransferRequest request) {
        Account target = accountRepository.findByAccountNumber(request.targetAccountNumber())
                .orElseThrow(() -> ResourceNotFoundException.account(request.targetAccountNumber()));

        if (target.getId().equals(request.sourceAccountId())) {
            throw BusinessRuleException.sameAccountTransfer();
        }

        // Trava as duas contas sempre na mesma ordem (por id) para evitar deadlock
        // quando duas transferencias cruzadas ocorrem ao mesmo tempo.
        List<UUID> lockOrder = Stream.of(request.sourceAccountId(), target.getId())
                .sorted(Comparator.naturalOrder())
                .toList();
        Account first = lockAccount(lockOrder.get(0));
        Account second = lockAccount(lockOrder.get(1));

        Account source = first.getId().equals(request.sourceAccountId()) ? first : second;
        Account destination = source == first ? second : first;

        accountService.ensureCanAccess(source.getOwner().getId(), currentUser);
        ensureOperational(source);
        ensureOperational(destination);

        BigDecimal amount = normalize(request.amount());
        if (!source.hasSufficientFunds(amount)) {
            throw BusinessRuleException.insufficientFunds();
        }

        source.debit(amount);
        destination.credit(amount);
        accountRepository.save(source);
        accountRepository.save(destination);

        UUID operationId = UUID.randomUUID();
        record(operationId, source, destination, TransactionType.TRANSFER_OUT, amount, request.description());
        record(operationId, destination, source, TransactionType.TRANSFER_IN, amount, request.description());

        cacheEvictor.evict(source);
        cacheEvictor.evict(destination);
        log.info("Transferencia de {} de {} para {}", amount, source.getAccountNumber(), destination.getAccountNumber());

        return new TransferResponse(
                operationId,
                source.getAccountNumber(),
                destination.getAccountNumber(),
                amount,
                source.getBalance(),
                Instant.now());
    }

    /**
     * Extrato paginado. O mapeamento para DTO acontece aqui dentro, ainda com a
     * sessao JPA aberta, porque {@code counterpartyAccount} e uma associacao lazy.
     */
    @Transactional(readOnly = true)
    public Page<TransactionResponse> statement(UUID accountId,
                                               AuthenticatedUser currentUser,
                                               TransactionType type,
                                               Instant from,
                                               Instant to,
                                               Pageable pageable) {
        accountService.getAccountFor(accountId, currentUser);
        return transactionRepository
                .findAll(TransactionSpecifications.statement(accountId, type, from, to), pageable)
                .map(TransactionResponse::from);
    }

    private Transaction record(UUID operationId,
                               Account account,
                               Account counterparty,
                               TransactionType type,
                               BigDecimal amount,
                               String description) {
        return transactionRepository.save(Transaction.builder()
                .operationId(operationId)
                .account(account)
                .counterpartyAccount(counterparty)
                .type(type)
                .amount(amount)
                .balanceAfter(account.getBalance())
                .description(description)
                .build());
    }

    private Account lockAccount(UUID accountId) {
        return accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
    }

    private void ensureOperational(Account account) {
        if (!account.isOperational()) {
            throw BusinessRuleException.accountNotOperational(account.getAccountNumber());
        }
    }

    private BigDecimal normalize(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
