package com.agendaonline.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    /** O front envia o token no cabeçalho {@code Authorization: Bearer <accessToken>}. */
    public record LoginResponse(String accessToken, String tokenType, Instant expiresAt) {
    }

    public record MeResponse(String email) {
    }
}
