package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.TopologicalSorter;
import java.util.List;

public record TopologicalOrderResponse(
        String repositoryId,
        boolean acyclic,
        String message,
        List<AnalyticsNodeResponse> order,
        List<List<String>> cycles
) {
    public static TopologicalOrderResponse from(String repositoryId, TopologicalSorter.TopologicalSortResult result) {
        return new TopologicalOrderResponse(
                repositoryId,
                result.acyclic(),
                result.message(),
                result.orderedNodes().stream().map(AnalyticsNodeResponse::from).toList(),
                result.cycles()
        );
    }
}
