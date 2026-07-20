package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.exception.UnauthorizedException;
import com.githubgraph.api.persistence.entity.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    @Test
    void issuesAndVerifiesSignedTokenForUser() {
        UserEntity user = new UserEntity();
        user.setId(UUID.fromString("00000000-0000-0000-0000-000000000123"));
        user.setEmail("user@example.com");
        TokenService service = new TokenService(new ObjectMapper(), properties());

        String token = service.issue(user);

        assertEquals(user.getId().toString(), service.subject(token));
    }

    @Test
    void rejectsTokenWithModifiedSignature() {
        UserEntity user = new UserEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("user@example.com");
        TokenService service = new TokenService(new ObjectMapper(), properties());
        String token = service.issue(user);

        assertThrows(UnauthorizedException.class, () -> service.subject(token + "x"));
    }

    private AppProperties properties() {
        return new AppProperties(
                new AppProperties.Analysis("http://analysis:8000"),
                new AppProperties.CloneProperties("/tmp/repos", 60, 1_000_000, 100),
                new AppProperties.Github("https://api.github.com", null),
                new AppProperties.Neo4jInitialization(1, 1, 1),
                new AppProperties.Auth(true, "test-token-secret", 3600, "local@example.com")
        );
    }
}
