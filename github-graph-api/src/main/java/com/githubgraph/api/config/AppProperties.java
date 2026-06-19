package com.githubgraph.api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Name;

@ConfigurationProperties(prefix = "github-graph")
public record AppProperties(
        Analysis analysis,
        @Name("clone") CloneProperties cloneProperties
) {
    public record Analysis(String baseUrl) {
    }

    public record CloneProperties(String root) {
    }
}
