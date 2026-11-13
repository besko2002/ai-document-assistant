package com.example.docassistant.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** Request and response bodies of the auth API. */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** The email is trimmed before validation so " Ada@Example.com " is accepted. */
    public record RegisterRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72) String password) {

        public RegisterRequest {
            name = name == null ? null : name.trim();
            email = email == null ? null : email.trim();
        }
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {

        public LoginRequest {
            email = email == null ? null : email.trim();
        }
    }

    public record UserResponse(UUID id, String name, String email) {
    }

    public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }
}
