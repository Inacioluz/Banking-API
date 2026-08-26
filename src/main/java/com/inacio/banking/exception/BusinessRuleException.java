package com.inacio.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Requisicao sintaticamente valida que viola uma regra de negocio.
 */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }

    public static BusinessRuleException insufficientFunds() {
        return new BusinessRuleException("INSUFFICIENT_FUNDS", "Saldo insuficiente para concluir a operacao");
    }

    public static BusinessRuleException accountNotOperational(String accountNumber) {
        return new BusinessRuleException("ACCOUNT_NOT_OPERATIONAL",
                "A conta " + accountNumber + " nao esta ativa e nao aceita movimentacoes");
    }

    public static BusinessRuleException sameAccountTransfer() {
        return new BusinessRuleException("SAME_ACCOUNT_TRANSFER", "A conta de origem e destino devem ser diferentes");
    }

    public static BusinessRuleException accountLimitReached(int limit) {
        return new BusinessRuleException("ACCOUNT_LIMIT_REACHED",
                "Limite de " + limit + " contas por cliente atingido");
    }
}
