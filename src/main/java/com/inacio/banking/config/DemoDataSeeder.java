package com.inacio.banking.config;

import com.inacio.banking.domain.Account;
import com.inacio.banking.domain.AccountStatus;
import com.inacio.banking.domain.AccountType;
import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import com.inacio.banking.domain.User;
import com.inacio.banking.repository.AccountRepository;
import com.inacio.banking.repository.TransactionRepository;
import com.inacio.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cria os dados de demonstracao citados na documentacao Swagger. Idempotente:
 * so age quando a base ainda nao tem usuarios. Desligue com SEED_ENABLED=false.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            log.debug("Base ja populada, seed ignorado");
            return;
        }

        User admin = userRepository.save(User.builder()
                .fullName("Administrador do Banco")
                .email("admin@banking.com")
                .document("11122233344")
                .passwordHash(passwordEncoder.encode("Admin@123"))
                .role(Role.ADMIN)
                .enabled(true)
                .build());

        User maria = userRepository.save(User.builder()
                .fullName("Maria Souza")
                .email("maria.souza@email.com")
                .document("39053344705")
                .passwordHash(passwordEncoder.encode("Senha@123"))
                .role(Role.USER)
                .enabled(true)
                .build());

        User joao = userRepository.save(User.builder()
                .fullName("Joao Pereira")
                .email("joao.pereira@email.com")
                .document("52998224725")
                .passwordHash(passwordEncoder.encode("Senha@123"))
                .role(Role.USER)
                .enabled(true)
                .build());

        openAccount(maria, "0001-4837291056", AccountType.CHECKING, new BigDecimal("2500.00"));
        openAccount(joao, "0001-9182736450", AccountType.CHECKING, new BigDecimal("780.50"));
        openAccount(joao, "0001-5647382910", AccountType.SAVINGS, new BigDecimal("12000.00"));

        log.info("Dados de demonstracao criados. Admin: {} | Clientes: {}, {}",
                admin.getEmail(), maria.getEmail(), joao.getEmail());
    }

    private void openAccount(User owner, String accountNumber, AccountType type, BigDecimal initialBalance) {
        Account account = accountRepository.save(Account.builder()
                .accountNumber(accountNumber)
                .owner(owner)
                .type(type)
                .status(AccountStatus.ACTIVE)
                .balance(initialBalance)
                .build());

        transactionRepository.save(Transaction.builder()
                .operationId(UUID.randomUUID())
                .account(account)
                .type(TransactionType.DEPOSIT)
                .amount(initialBalance)
                .balanceAfter(initialBalance)
                .description("Deposito de abertura de conta")
                .build());
    }
}
