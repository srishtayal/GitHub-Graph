package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.exception.UnauthorizedException;
import com.githubgraph.api.persistence.entity.UserEntity;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class TokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final String HEADER = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9";

    private final ObjectMapper objectMapper;
    private final AppProperties properties;

    public TokenService(ObjectMapper objectMapper, AppProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public String issue(UserEntity user) {
        long issuedAt = Instant.now().getEpochSecond();
        long expiresAt = issuedAt + properties.auth().tokenTtlSeconds();
        try {
            String payload = encode(objectMapper.writeValueAsBytes(Map.of(
                    "sub", user.getId().toString(),
                    "email", user.getEmail(),
                    "iat", issuedAt,
                    "exp", expiresAt
            )));
            String signingInput = HEADER + "." + payload;
            return signingInput + "." + encode(sign(signingInput));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to issue access token", exception);
        }
    }

    public String subject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3 || !constantTimeEquals(sign(parts[0] + "." + parts[1]), decode(parts[2]))) {
            throw new UnauthorizedException("Invalid access token");
        }
        try {
            JsonNode payload = objectMapper.readTree(decode(parts[1]));
            if (payload.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
                throw new UnauthorizedException("Access token has expired");
            }
            String subject = payload.path("sub").asText();
            if (subject.isBlank()) {
                throw new UnauthorizedException("Access token subject is missing");
            }
            return subject;
        } catch (IOException exception) {
            throw new UnauthorizedException("Invalid access token payload");
        }
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(properties.auth().tokenSecret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to sign access token", exception);
        }
    }

    private boolean constantTimeEquals(byte[] expected, byte[] actual) {
        if (expected.length != actual.length) {
            return false;
        }
        int difference = 0;
        for (int index = 0; index < expected.length; index++) {
            difference |= expected[index] ^ actual[index];
        }
        return difference == 0;
    }

    private String encode(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private byte[] decode(String value) {
        try {
            return Base64.getUrlDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new UnauthorizedException("Invalid access token encoding");
        }
    }
}
