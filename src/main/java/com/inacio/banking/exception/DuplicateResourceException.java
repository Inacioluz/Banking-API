package com.inacio.banking.exception;

import org.springframework.http.HttpStatus;

public class DuplicateResourceException extends ApiException {

    public DuplicateResourceException(String message) {
        super(HttpStatus.CONFLICT, "RESOURCE_ALREADY_EXISTS", message);
    }
}
