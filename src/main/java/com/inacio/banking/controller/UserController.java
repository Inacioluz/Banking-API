package com.inacio.banking.controller;

import com.inacio.banking.dto.auth.UserResponse;
import com.inacio.banking.exception.ResourceNotFoundException;
import com.inacio.banking.repository.UserRepository;
import com.inacio.banking.security.AuthenticatedUser;
import com.inacio.banking.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Usuarios")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    @Operation(
            summary = "Dados do usuario autenticado",
            description = "Retorna o perfil correspondente ao token enviado. O documento vem mascarado.")
    @ApiResponse(responseCode = "200", description = "Perfil do usuario",
            content = @Content(schema = @Schema(implementation = UserResponse.class)))
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@CurrentUser AuthenticatedUser currentUser) {
        return userRepository.findById(currentUser.getId())
                .map(UserResponse::from)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> ResourceNotFoundException.user(currentUser.getId()));
    }
}
