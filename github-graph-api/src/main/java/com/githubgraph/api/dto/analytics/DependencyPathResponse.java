package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.DepthFirstTraversal;
import java.util.List;

public record DependencyPathResponse(
        String repositoryId,
        String startNodeId,
        List<TraversalNode> traversalOrder
) {
    public static DependencyPathResponse from(String repositoryId, DepthFirstTraversal.TraversalResult result) {
        return new DependencyPathResponse(
                repositoryId,
                result.startNodeId(),
                result.records().stream()
                        .map(record -> new TraversalNode(
                                AnalyticsNodeResponse.from(record.node()),
                                record.depth(),
                                record.predecessorNodeId(),
                                record.viaEdgeType()
                        ))
                        .toList()
        );
    }

    public record TraversalNode(
            AnalyticsNodeResponse node,
            int depth,
            String predecessorNodeId,
            String viaEdgeType
    ) {
    }
}
