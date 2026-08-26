package com.inacio.banking.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Segredo HMAC-SHA256. Deve ter no minimo 32 caracteres. */
    @NotBlank(message = "app.jwt.secret e obrigatorio")
    @Size(min = 32, message = "app.jwt.secret deve ter no minimo 32 caracteres")
    private String secret;

    /** Emissor gravado na claim {@code iss}. */
    private String issuer = "banking-api";

    /** Validade do token de acesso. */
    private Duration accessTokenExpiration = Duration.ofHours(1);

    /** Validade do refresh token. */
    private Duration refreshTokenExpiration = Duration.ofDays(7);
}
