package com.inacio.banking.repository;

import com.inacio.banking.domain.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * O extrato usa {@link JpaSpecificationExecutor} em vez de uma JPQL com filtros
 * opcionais: um {@code :param is null} manda um NULL sem tipo para o PostgreSQL,
 * que responde "could not determine data type of parameter". A Specification
 * monta apenas os predicados realmente informados.
 */
@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>,
        JpaSpecificationExecutor<Transaction> {

    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(UUID accountId, Pageable pageable);
}
