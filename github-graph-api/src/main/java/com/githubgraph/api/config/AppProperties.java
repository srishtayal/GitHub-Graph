package com.githubgraph.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "github-graph")
public record AppProperties(
        Analysis analysis,
        @Name("clone") CloneProperties cloneProperties,
        Github github,
        Neo4jInitialization neo4jInitialization
) {
    public record Analysis(String baseUrl) {
    }

    public record CloneProperties(
            String root,
            long timeoutSeconds,
            long maxSizeBytes,
            long pollIntervalMillis
    ) {
    }

    public record Github(String apiBaseUrl, String token) {
    }

    public record Neo4jInitialization(
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis
    ) {
    }
}
