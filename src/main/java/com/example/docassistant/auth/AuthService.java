package com.example.docassistant.auth;

import com.example.docassistant.auth.AuthDtos.AuthResponse;
import com.example.docassistant.auth.AuthDtos.LoginRequest;
import com.example.docassistant.auth.AuthDtos.RegisterRequest;
import com.example.docassistant.auth.AuthDtos.UserResponse;
import com.example.docassistant.common.ConflictException;
import com.example.docassistant.common.ResourceNotFoundException;
import com.example.docassistant.common.UnauthorizedException;
import com.example.docassistant.security.JwtService;
import com.example.docassistant.user.User;
import com.example.docassistant.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    /** Same message for an unknown email and a wrong password, so the API leaks nothing. */
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;

    AuthService(UserRepository users, PasswordEncoder encoder, JwtService jwt) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalise(request.email());
        if (users.existsByEmail(email)) {
            throw new ConflictException("Email is already registered");
        }
        try {
            User saved = users.saveAndFlush(new User(request.name(), email, encoder.encode(request.password())));
            return respond(saved);
        } catch (DataIntegrityViolationException e) {
            // Two registrations racing: the unique index decides.
            throw new ConflictException("Email is already registered");
        }
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByEmail(normalise(request.email()))
                .orElseThrow(() -> new UnauthorizedException(INVALID_CREDENTIALS));
        if (!encoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(INVALID_CREDENTIALS);
        }
        return respond(user);
    }

    @Transactional(readOnly = true)
    public UserResponse me(UUID userId) {
        User user = users.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found"));
        return toResponse(user);
    }

    private AuthResponse respond(User user) {
        return new AuthResponse(jwt.issue(user), "Bearer", jwt.expiresInSeconds(), toResponse(user));
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail());
    }

    private static String normalise(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
