package com.githubgraph.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record GraphAnalyticsRequest(
        JsonNode graph,
        String nodeId,
        Integer maxDepth
) {
    public GraphAnalyticsRequest(JsonNode graph) {
        this(graph, null, 10);
    }
}
