package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.exception.ExternalServiceException;
import com.githubgraph.api.exception.ValidationException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceClientService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public IntelligenceClientService(AppProperties properties, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.baseUrl = properties.analysis().baseUrl();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public JsonNode rankSimilarity(Object request) {
        return post("/internal/v1/intelligence/similarity", request);
    }

    public JsonNode cluster(Object request) {
        return post("/internal/v1/intelligence/clusters", request);
    }

    public JsonNode localize(Object request) {
        return post("/internal/v1/intelligence/localize", request);
    }

    private JsonNode post(String path, Object request) {
        try {
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(90))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(request)))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return objectMapper.readTree(response.body());
            }
            if (response.statusCode() >= 400 && response.statusCode() < 500) {
                throw new ValidationException(
                        "INTELLIGENCE_REQUEST_INVALID",
                        "Intelligence request was rejected: " + response.body()
                );
            }
            throw new ExternalServiceException(
                    "INTELLIGENCE_SERVICE_FAILED",
                    "Intelligence service failed with status " + response.statusCode(),
                    null
            );
        } catch (ValidationException | ExternalServiceException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ExternalServiceException(
                    "INTELLIGENCE_SERVICE_INTERRUPTED",
                    "Intelligence service request was interrupted",
                    exception
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize intelligence request", exception);
        } catch (IOException exception) {
            throw new ExternalServiceException(
                    "INTELLIGENCE_SERVICE_UNAVAILABLE",
                    "Intelligence service is unavailable",
                    exception
            );
        }
    }
}
