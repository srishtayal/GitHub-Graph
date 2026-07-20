package com.githubgraph.api.dto.auth;

public record AuthResponse(String accessToken, UserResponse user, long expiresInSeconds) {
}
