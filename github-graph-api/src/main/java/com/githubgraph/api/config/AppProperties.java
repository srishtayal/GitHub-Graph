package com.githubgraph.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "github-graph")
public record AppProperties(
        Analysis analysis,
        @Name("clone") CloneProperties cloneProperties,
        Github github,
        Neo4jInitialization neo4jInitialization,
        Auth auth
) {
    @ConstructorBinding
    public AppProperties(
            Analysis analysis,
            CloneProperties cloneProperties,
            Github github,
            Neo4jInitialization neo4jInitialization,
            Auth auth
    ) {
        this.analysis = analysis;
        this.cloneProperties = cloneProperties;
        this.github = github;
        this.neo4jInitialization = neo4jInitialization;
        this.auth = auth;
    }

    public AppProperties(
            Analysis analysis,
            CloneProperties cloneProperties,
            Github github,
            Neo4jInitialization neo4jInitialization
    ) {
        this(analysis, cloneProperties, github, neo4jInitialization,
                new Auth(false, "change-this-before-deployment", 28800, "local@github-graph.dev"));
    }

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

    public record Auth(
            boolean enabled,
            String tokenSecret,
            long tokenTtlSeconds,
            String developmentEmail
    ) {
    }
}
