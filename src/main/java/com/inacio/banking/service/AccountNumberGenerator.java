package com.inacio.banking.service;

import com.inacio.banking.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Gera numeros de conta no formato {@code 0001-XXXXXXXXXX}, verificando colisao
 * no banco antes de devolver.
 */
@Component
@RequiredArgsConstructor
public class AccountNumberGenerator {

    private static final String BRANCH = "0001";
    private static final int MAX_ATTEMPTS = 10;

    private final SecureRandom random = new SecureRandom();
    private final AccountRepository accountRepository;

    public String generate() {
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            String candidate = BRANCH + "-" + random.nextLong(1_000_000_000L, 10_000_000_000L);
            if (!accountRepository.existsByAccountNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Nao foi possivel gerar um numero de conta unico");
    }
}
