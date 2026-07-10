package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.BreadthFirstTraversal;
import java.util.List;

public record ImpactAnalysisResponse(
        String repositoryId,
        String startNodeId,
        int totalAffectedNodes,
        List<ImpactNode> affectedNodes
) {
    public static ImpactAnalysisResponse from(String repositoryId, BreadthFirstTraversal.TraversalResult result) {
        List<ImpactNode> nodes = result.records().stream()
                .map(record -> new ImpactNode(
                        AnalyticsNodeResponse.from(record.node()),
                        record.depth(),
                        record.predecessorNodeId(),
                        record.viaEdgeType()
                ))
                .toList();
        return new ImpactAnalysisResponse(repositoryId, result.startNodeId(), Math.max(nodes.size() - 1, 0), nodes);
    }

    public record ImpactNode(
            AnalyticsNodeResponse node,
            int depth,
            String predecessorNodeId,
            String viaEdgeType
    ) {
    }
}
