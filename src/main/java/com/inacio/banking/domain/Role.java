package com.inacio.banking.domain;

/**
 * Perfis de acesso da aplicacao.
 */
public enum Role {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
