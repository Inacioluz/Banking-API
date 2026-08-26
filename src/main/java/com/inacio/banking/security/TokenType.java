package com.inacio.banking.security;

/**
 * Distingue o proposito do JWT — gravado na claim {@code typ} para impedir que
 * um refresh token seja usado como token de acesso.
 */
public enum TokenType {
    ACCESS,
    REFRESH
}
