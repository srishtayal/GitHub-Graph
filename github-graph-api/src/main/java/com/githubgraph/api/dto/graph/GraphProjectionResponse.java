package com.githubgraph.api.dto.graph;

import java.util.List;
import java.util.Map;

public record GraphProjectionResponse(
        String repositoryId,
        String snapshotId,
        String level,
        String rootId,
        int suggestedMaximumNodes,
        boolean truncated,
        ProjectionTotals totals,
        List<ProjectedNode> nodes,
        List<ProjectedEdge> edges
) {

    public record ProjectionTotals(
            int rawNodeCount,
            int rawEdgeCount,
            int projectedNodeCount,
            int projectedEdgeCount
    ) {
    }

    public record ProjectedNode(
            String id,
            String displayName,
            String level,
            String category,
            NodeCounts counts,
            int incomingDependencyCount,
            int outgoingDependencyCount,
            double criticalityScore,
            int childCount,
            List<RepresentativeReference> representatives,
            List<String> underlyingNodeIds,
            boolean expandable
    ) {
    }

    public record NodeCounts(
            int files,
            int classes,
            int functions,
            int routes
    ) {
    }

    public record RepresentativeReference(
            String id,
            String displayName,
            String type
    ) {
    }

    public record ProjectedEdge(
            String id,
            String source,
            String target,
            String type,
            int totalRelationshipCount,
            Map<String, Integer> countsByType,
            List<String> underlyingEdgeIds
    ) {
    }
}
