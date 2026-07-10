package com.githubgraph.api.analytics.model;

import java.util.Map;

public record GraphNodeView(
        String id,
        String type,
        String label,
        Map<String, Object> properties
) {
}
