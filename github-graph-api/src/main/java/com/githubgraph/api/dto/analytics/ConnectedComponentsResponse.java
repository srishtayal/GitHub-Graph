package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.ConnectedComponentsFinder;
import java.util.List;

public record ConnectedComponentsResponse(
        String repositoryId,
        int totalComponents,
        List<ComponentResponse> components
) {
    public static ConnectedComponentsResponse from(String repositoryId, ConnectedComponentsFinder.ComponentsResult result) {
        List<ComponentResponse> components = result.components().stream()
                .map(component -> new ComponentResponse(
                        component.id(),
                        component.nodes().size(),
                        component.nodes().stream().map(AnalyticsNodeResponse::from).toList()
                ))
                .toList();
        return new ConnectedComponentsResponse(repositoryId, components.size(), components);
    }

    public record ComponentResponse(
            String id,
            int size,
            List<AnalyticsNodeResponse> nodes
    ) {
    }
}
