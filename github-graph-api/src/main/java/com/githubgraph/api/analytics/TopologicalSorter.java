package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TopologicalSorter {

    /**
     * Computes topological order for the dependency graph. Complexity: O(V + E).
     */
    public TopologicalSortResult sort(GraphView graph) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, GraphNodeView> nodes = graph.nodesById();
        nodes.keySet().forEach(nodeId -> inDegree.put(nodeId, 0));

        for (GraphEdgeView edge : graph.dependencyEdges()) {
            inDegree.computeIfPresent(edge.target(), (ignored, degree) -> degree + 1);
        }

        ArrayDeque<String> queue = new ArrayDeque<>();
        inDegree.forEach((nodeId, degree) -> {
            if (degree == 0) {
                queue.addLast(nodeId);
            }
        });

        List<GraphNodeView> orderedNodes = new ArrayList<>();
        Map<String, Integer> mutableInDegree = new HashMap<>(inDegree);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            GraphNodeView node = nodes.get(current);
            if (node != null) {
                orderedNodes.add(node);
            }

            for (GraphEdgeView edge : graph.outgoingDependencyEdges(current)) {
                int nextValue = mutableInDegree.computeIfPresent(edge.target(), (ignored, degree) -> degree - 1);
                if (nextValue == 0) {
                    queue.addLast(edge.target());
                }
            }
        }

        if (orderedNodes.size() != nodes.size()) {
            CycleDetector.CycleResult cycleResult = new CycleDetector().detectCycles(graph);
            return new TopologicalSortResult(false, List.of(), "Cycles detected in dependency graph", cycleResult.cycles());
        }

        return new TopologicalSortResult(true, orderedNodes, null, List.of());
    }

    public record TopologicalSortResult(
            boolean acyclic,
            List<GraphNodeView> orderedNodes,
            String message,
            List<List<String>> cycles
    ) {
    }
}
