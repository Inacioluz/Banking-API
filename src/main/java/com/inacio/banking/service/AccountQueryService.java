package com.inacio.banking.service;

import com.inacio.banking.config.CacheConfig;
import com.inacio.banking.dto.account.AccountResponse;
import com.inacio.banking.dto.account.BalanceResponse;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Leituras cacheadas de conta. Vive em um bean separado de propósito: chamar um
 * metodo {@code @Cacheable} de dentro da mesma classe ignora o proxy do Spring e
 * o cache nunca seria consultado.
 *
 * <p>Nenhum metodo aqui verifica titularidade — a autorizacao acontece em
 * {@link AccountService}, sobre o resultado ja cacheado.
 */
@Service
@RequiredArgsConstructor
public class AccountQueryService {

    private final AccountRepository accountRepository;

    @Cacheable(cacheNames = CacheConfig.ACCOUNTS_CACHE, key = "#accountId")
    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AccountResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
    }

    @Cacheable(cacheNames = CacheConfig.BALANCES_CACHE, key = "#accountId")
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(BalanceResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.account(accountId));
    }

    @Cacheable(cacheNames = CacheConfig.ACCOUNTS_BY_OWNER_CACHE, key = "#ownerId")
    @Transactional(readOnly = true)
    public List<AccountResponse> listByOwner(UUID ownerId) {
        return accountRepository.findByOwnerIdOrderByCreatedAtAsc(ownerId).stream()
                .map(AccountResponse::from)
                .toList();
    }
}
