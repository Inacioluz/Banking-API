package com.inacio.banking.dto.auth;

import com.inacio.banking.domain.Role;
import com.inacio.banking.domain.User;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.UUID;

@Schema(name = "UserResponse", description = "Representacao publica do usuario")
public record UserResponse(

        @Schema(description = "Identificador do usuario", example = "8e9f1c2a-3b4d-4e5f-8a9b-0c1d2e3f4a5b")
        UUID id,

        @Schema(description = "Nome completo", example = "Maria Souza")
        String fullName,

        @Schema(description = "E-mail cadastrado", example = "maria.souza@email.com")
        String email,

        @Schema(description = "Documento mascarado", example = "***.533.447-**")
        String document,

        @Schema(description = "Perfil de acesso", example = "USER")
        Role role,

        @Schema(description = "Data de criacao do cadastro", example = "2026-08-25T13:45:30Z")
        Instant createdAt
) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getFullName(),
                user.getEmail(),
                maskDocument(user.getDocument()),
                user.getRole(),
                user.getCreatedAt()
        );
    }

    private static String maskDocument(String document) {
        if (document == null || document.length() != 11) {
            return "***";
        }
        return "***." + document.substring(3, 6) + "." + document.substring(6, 9) + "-**";
    }
}
