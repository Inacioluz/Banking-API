package com.inacio.banking.repository;

import com.inacio.banking.domain.Transaction;
import com.inacio.banking.domain.TransactionType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Predicados de consulta do extrato.
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    /**
     * Lancamentos de uma conta, com filtros opcionais de tipo e periodo. Cada
     * filtro nulo simplesmente nao vira predicado.
     */
    public static Specification<Transaction> statement(UUID accountId,
                                                       TransactionType type,
                                                       Instant from,
                                                       Instant to) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("account").get("id"), accountId));

            if (type != null) {
                predicates.add(builder.equal(root.get("type"), type));
            }
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
