package com.inacio.banking.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenOperationException extends ApiException {

    public ForbiddenOperationException(String message) {
        super(HttpStatus.FORBIDDEN, "FORBIDDEN", message);
    }

    public static ForbiddenOperationException notAccountOwner() {
        return new ForbiddenOperationException("Voce nao tem permissao para operar esta conta");
    }
}
