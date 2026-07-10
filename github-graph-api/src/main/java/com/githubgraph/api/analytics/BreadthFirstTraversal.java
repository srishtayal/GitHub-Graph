package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BreadthFirstTraversal {

    /**
     * Computes impact by traversing incoming dependency edges. Complexity: O(V + E).
     */
    public TraversalResult traverseImpact(GraphView graph, String startNodeId) {
        validateStartNode(graph, startNodeId);

        ArrayDeque<TraversalRecord> queue = new ArrayDeque<>();
        Set<String> visited = new LinkedHashSet<>();
        List<TraversalRecord> records = new ArrayList<>();

        GraphNodeView startNode = graph.node(startNodeId);
        queue.add(new TraversalRecord(startNode, 0, null, null));
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            TraversalRecord current = queue.removeFirst();
            records.add(current);

            for (GraphEdgeView edge : graph.incomingDependencyEdges(current.node().id())) {
                GraphNodeView dependent = graph.node(edge.source());
                if (dependent == null || visited.contains(dependent.id())) {
                    continue;
                }
                visited.add(dependent.id());
                queue.addLast(new TraversalRecord(
                        dependent,
                        current.depth() + 1,
                        current.node().id(),
                        edge.normalizedType()
                ));
            }
        }

        return new TraversalResult(startNodeId, records);
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
