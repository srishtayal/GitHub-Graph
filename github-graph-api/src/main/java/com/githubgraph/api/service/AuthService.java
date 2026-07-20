package com.githubgraph.api.service;

import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.dto.auth.AuthResponse;
import com.githubgraph.api.dto.auth.LoginRequest;
import com.githubgraph.api.dto.auth.RegisterRequest;
import com.githubgraph.api.dto.auth.UserResponse;
import com.githubgraph.api.exception.UnauthorizedException;
import com.githubgraph.api.exception.ValidationException;
import com.githubgraph.api.persistence.entity.UserEntity;
import com.githubgraph.api.persistence.repository.UserJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserJpaRepository userJpaRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final AppProperties properties;
    private final ObjectProvider<HttpServletRequest> requestProvider;

    public AuthService(
            UserJpaRepository userJpaRepository,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            AppProperties properties,
            ObjectProvider<HttpServletRequest> requestProvider
    ) {
        this.userJpaRepository = userJpaRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.properties = properties;
        this.requestProvider = requestProvider;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userJpaRepository.findByEmailIgnoreCase(email).isPresent()) {
            throw new ValidationException("EMAIL_ALREADY_REGISTERED", "An account with this email already exists");
        }
        UserEntity user = new UserEntity();
        user.setEmail(email);
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        return response(userJpaRepository.save(user));
    }

    public AuthResponse login(LoginRequest request) {
        UserEntity user = userJpaRepository.findByEmailIgnoreCase(normalizeEmail(request.email()))
                .orElseThrow(() -> new UnauthorizedException("Email or password is incorrect"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Email or password is incorrect");
        }
        return response(user);
    }

    @Transactional
    public UserEntity currentUser() {
        if (!properties.auth().enabled()) {
            return developmentUser();
        }
        HttpServletRequest request = requestProvider.getIfAvailable();
        String header = request != null ? request.getHeader("Authorization") : null;
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("A Bearer access token is required");
        }
        UUID userId;
        try {
            userId = UUID.fromString(tokenService.subject(header.substring("Bearer ".length()).trim()));
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Access token subject is invalid");
        }
        return userJpaRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("Access token user no longer exists"));
    }

    public UserResponse currentUserResponse() {
        return toResponse(currentUser());
    }

    public boolean authenticationEnabled() {
        return properties.auth().enabled();
    }

    private UserEntity developmentUser() {
        String email = normalizeEmail(properties.auth().developmentEmail());
        return userJpaRepository.findByEmailIgnoreCase(email).orElseGet(() -> {
            UserEntity user = new UserEntity();
            user.setEmail(email);
            user.setDisplayName("Local Workspace");
            user.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
            return userJpaRepository.save(user);
        });
    }

    private AuthResponse response(UserEntity user) {
        return new AuthResponse(tokenService.issue(user), toResponse(user), properties.auth().tokenTtlSeconds());
    }

    private UserResponse toResponse(UserEntity user) {
        return new UserResponse(user.getId().toString(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
