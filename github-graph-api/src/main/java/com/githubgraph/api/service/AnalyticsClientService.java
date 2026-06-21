package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.dto.GraphAnalyticsRequest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsClientService {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final Logger log = LoggerFactory.getLogger(AnalyticsClientService.class);

    public AnalyticsClientService(AppProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.analysis().baseUrl();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public JsonNode analyzeGraph(GraphAnalyticsRequest request) {
        try {
            ObjectNode payload = objectMapper.createObjectNode();
            payload.set("graph", request.graph());
            if (request.nodeId() != null && !request.nodeId().isBlank()) {
                payload.put("nodeId", request.nodeId());
            }
            payload.put("maxDepth", request.maxDepth() != null ? request.maxDepth() : 10);

            String json = objectMapper.writeValueAsString(payload);
            String url = baseUrl + "/internal/v1/graph-analytics";
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            log.info("POST {} -> sending graph analytics request", url);
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            String body = response.body();
            log.info("Analytics service responded: {} {}", status, body);

            if (status == 404) {
                throw new IllegalArgumentException(body);
            }
            if (status >= 200 && status < 300) {
                return objectMapper.readTree(body);
            }
            throw new IllegalStateException(status + " " + body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize analytics request", exception);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call analytics service", exception);
        }
    }
}
