package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.model.GraphNodeView;
import java.util.Map;

public record AnalyticsNodeResponse(
        String id,
        String type,
        String label,
        Map<String, Object> properties
) {
    public static AnalyticsNodeResponse from(GraphNodeView node) {
        return new AnalyticsNodeResponse(node.id(), node.type(), node.label(), node.properties());
    }
}
