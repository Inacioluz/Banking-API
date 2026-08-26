package com.inacio.banking.security;

import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injeta o {@link AuthenticatedUser} da requisicao em um parametro de controller
 * sem que o springdoc o exponha como parametro na documentacao.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@AuthenticationPrincipal
@Parameter(hidden = true, in = ParameterIn.HEADER)
@Hidden
public @interface CurrentUser {
}
