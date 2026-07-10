package com.githubgraph.api.analytics.model;

import java.util.Locale;
import java.util.Map;

public record GraphEdgeView(
        String id,
        String source,
        String target,
        String type,
        Map<String, Object> properties
) {
    public String normalizedType() {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "BELONGS_TO" -> "CONTAINS";
            case "INHERITS" -> "EXTENDS";
            default -> type.toUpperCase(Locale.ROOT);
        };
    }

    public boolean isDependencyEdge() {
        return GraphView.DEPENDENCY_EDGE_TYPES.contains(normalizedType());
    }

    public boolean isContainmentEdge() {
        return GraphView.CONTAINMENT_EDGE_TYPES.contains(normalizedType());
    }
}
