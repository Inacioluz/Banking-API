package com.inacio.banking.domain;

/**
 * Situacao operacional da conta.
 */
public enum AccountStatus {
    /** Conta ativa, aceita todas as operacoes. */
    ACTIVE,
    /** Conta bloqueada, nao aceita movimentacoes. */
    BLOCKED,
    /** Conta encerrada. */
    CLOSED
}
