package com.inacio.banking.domain;

/**
 * Natureza do lancamento registrado no extrato.
 */
public enum TransactionType {
    /** Entrada de valor por deposito. */
    DEPOSIT,
    /** Saida de valor por saque. */
    WITHDRAWAL,
    /** Saida de valor por transferencia enviada. */
    TRANSFER_OUT,
    /** Entrada de valor por transferencia recebida. */
    TRANSFER_IN;

    public boolean isCredit() {
        return this == DEPOSIT || this == TRANSFER_IN;
    }
}
