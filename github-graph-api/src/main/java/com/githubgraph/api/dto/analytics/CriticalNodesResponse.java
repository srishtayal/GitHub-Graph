package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.CentralityAnalyzer;
import java.util.List;

public record CriticalNodesResponse(
        String repositoryId,
        int totalReturned,
        List<CriticalNode> nodes
) {
    public static CriticalNodesResponse from(String repositoryId, CentralityAnalyzer.CentralityResult result) {
        List<CriticalNode> nodes = result.nodes().stream()
                .map(item -> new CriticalNode(
                        AnalyticsNodeResponse.from(item.node()),
                        item.inDegree(),
                        item.outDegree(),
                        item.totalDegree(),
                        item.degreeCentrality()
                ))
                .toList();
        return new CriticalNodesResponse(repositoryId, nodes.size(), nodes);
    }

    public record CriticalNode(
            AnalyticsNodeResponse node,
            int inDegree,
            int outDegree,
            int totalDegree,
            double degreeCentrality
    ) {
    }
}
