package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DepthFirstTraversal {

    /**
     * Traces outgoing dependency chains from the starting node. Complexity: O(V + E).
     */
    public TraversalResult traceDependencies(GraphView graph, String startNodeId) {
        validateStartNode(graph, startNodeId);

        List<TraversalRecord> records = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        dfs(graph, startNodeId, 0, null, null, visited, records);
        return new TraversalResult(startNodeId, records);
    }

    private void dfs(
            GraphView graph,
            String currentNodeId,
            int depth,
            String predecessorNodeId,
            String viaEdgeType,
            Set<String> visited,
            List<TraversalRecord> records
    ) {
        visited.add(currentNodeId);
        GraphNodeView currentNode = graph.node(currentNodeId);
        records.add(new TraversalRecord(currentNode, depth, predecessorNodeId, viaEdgeType));

        for (GraphEdgeView edge : graph.outgoingDependencyEdges(currentNodeId)) {
            if (!visited.contains(edge.target())) {
                dfs(graph, edge.target(), depth + 1, currentNodeId, edge.normalizedType(), visited, records);
            }
        }
    }

    private void validateStartNode(GraphView graph, String startNodeId) {
        if (!graph.containsNode(startNodeId)) {
            throw new IllegalArgumentException("Unknown nodeId: " + startNodeId);
        }
    }

    public record TraversalResult(
            String startNodeId,
            List<TraversalRecord> records
    ) {
    }

    public record TraversalRecord(
            GraphNodeView node,
            int depth,
            String predecessorNodeId,
            String viaEdgeType
    ) {
    }
}
