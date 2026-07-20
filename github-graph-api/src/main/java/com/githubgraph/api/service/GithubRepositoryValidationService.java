package com.githubgraph.api.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.exception.ExternalServiceException;
import com.githubgraph.api.exception.ValidationException;
import com.githubgraph.api.util.GithubRepoRef;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class GithubRepositoryValidationService {

    private final RestClient restClient;

    public GithubRepositoryValidationService(RestClient.Builder builder, AppProperties properties) {
        RestClient.Builder configured = builder
                .baseUrl(properties.github().apiBaseUrl())
                .defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
                .defaultHeader(HttpHeaders.USER_AGENT, "github-graph");
        if (properties.github().token() != null && !properties.github().token().isBlank()) {
            configured.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.github().token());
        }
        this.restClient = configured.build();
    }

    public void verifyPublic(GithubRepoRef repository) {
        try {
            GithubRepositoryMetadata metadata = restClient.get()
                    .uri("/repos/{owner}/{repository}", repository.owner(), repository.name())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GithubRepositoryMetadata.class);

            if (metadata == null) {
                throw new ExternalServiceException(
                        "GITHUB_INVALID_RESPONSE",
                        "GitHub returned an empty repository response",
                        null
                );
            }
            if (metadata.privateRepository()) {
                throw new ValidationException(
                        "PRIVATE_REPOSITORY",
                        "Private GitHub repositories are not supported"
                );
            }
        } catch (ValidationException | ExternalServiceException exception) {
            throw exception;
        } catch (HttpClientErrorException exception) {
            if (exception.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new ValidationException(
                        "REPOSITORY_NOT_FOUND",
                        "GitHub repository was not found or is not publicly accessible"
                );
            }
            if (exception.getStatusCode() == HttpStatus.FORBIDDEN
                    || exception.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                throw new ExternalServiceException(
                        "GITHUB_RATE_LIMITED",
                        "GitHub repository validation is temporarily rate-limited",
                        exception
                );
            }
            throw new ExternalServiceException(
                    "GITHUB_VALIDATION_FAILED",
                    "GitHub repository validation failed",
                    exception
            );
        } catch (RestClientException exception) {
            throw new ExternalServiceException(
                    "GITHUB_UNAVAILABLE",
                    "GitHub repository validation is temporarily unavailable",
                    exception
            );
        }
    }

    record GithubRepositoryMetadata(
            @JsonProperty("private") boolean privateRepository
    ) {
    }
}
