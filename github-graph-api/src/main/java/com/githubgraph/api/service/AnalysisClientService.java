package com.githubgraph.api.service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.dto.AnalysisServiceRequest;
import com.githubgraph.api.dto.AnalysisServiceResponse;

@Service
public class AnalysisClientService {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final Logger log = LoggerFactory.getLogger(AnalysisClientService.class);

    public AnalysisClientService(AppProperties properties, ObjectMapper objectMapper) {
        this.baseUrl = properties.analysis().baseUrl();
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public AnalysisServiceResponse analyze(AnalysisServiceRequest request) {
        try {
            String json = objectMapper.writeValueAsString(request);
                String url = baseUrl + "/internal/v1/analysis-jobs";
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

                log.info("POST {} -> sending payload: {}", url, json);

                HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

                int status = response.statusCode();
                String body = response.body();
                log.info("Analysis service responded: {} {}", status, body);
            if (status >= 200 && status < 300) {
                return objectMapper.readValue(body, AnalysisServiceResponse.class);
            }
            throw new IllegalStateException(status + " " + body);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize analysis request", exception);
        } catch (IOException | InterruptedException exception) {
            throw new IllegalStateException("Failed to call analysis service", exception);
        }
    }
}
