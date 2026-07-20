package com.githubgraph.api.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.githubgraph.api.exception.ValidationException;
import org.junit.jupiter.api.Test;

class GithubUrlValidatorTest {

    private final GithubUrlValidator validator = new GithubUrlValidator();

    @Test
    void normalizesValidPublicRepositoryUrl() {
        GithubRepoRef result = validator.validateAndNormalize("https://github.com/pallets/flask.git/");

        assertEquals("https://github.com/pallets/flask", result.normalizedUrl());
        assertEquals("pallets", result.owner());
        assertEquals("flask", result.name());
    }

    @Test
    void rejectsInvalidRepositoryUrlBeforeRemoteValidation() {
        ValidationException exception = assertThrows(
                ValidationException.class,
                () -> validator.validateAndNormalize("https://gitlab.com/pallets/flask")
        );

        assertEquals("UNSUPPORTED_GITHUB_URL", exception.getCode());
    }
}
