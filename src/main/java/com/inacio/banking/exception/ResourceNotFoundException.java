package com.inacio.banking.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
    }

    public static ResourceNotFoundException account(Object identifier) {
        return new ResourceNotFoundException("Conta nao encontrada: " + identifier);
    }

    public static ResourceNotFoundException user(Object identifier) {
        return new ResourceNotFoundException("Usuario nao encontrado: " + identifier);
    }
}
