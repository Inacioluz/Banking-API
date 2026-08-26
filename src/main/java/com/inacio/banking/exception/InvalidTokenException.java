package com.inacio.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Refresh token invalido, expirado, revogado ou ja utilizado. Distinta de
 * credenciais invalidas, para que a mensagem devolvida no refresh nao fale de
 * e-mail e senha.
 */
public class InvalidTokenException extends ApiException {

    public InvalidTokenException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                "Refresh token invalido, expirado ou ja utilizado");
    }
}
