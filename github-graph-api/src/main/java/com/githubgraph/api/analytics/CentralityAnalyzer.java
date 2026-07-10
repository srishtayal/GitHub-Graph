package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CentralityAnalyzer {

    /**
     * Computes in-degree, out-degree, and degree centrality. Complexity: O(V + E).
     */
    public CentralityResult analyze(GraphView graph, int limit) {
        Map<String, Integer> inDegree = new LinkedHashMap<>();
        Map<String, Integer> outDegree = new LinkedHashMap<>();
        graph.nodesById().keySet().forEach(nodeId -> {
            inDegree.put(nodeId, 0);
            outDegree.put(nodeId, 0);
        });

        for (GraphEdgeView edge : graph.dependencyEdges()) {
            outDegree.computeIfPresent(edge.source(), (ignored, degree) -> degree + 1);
            inDegree.computeIfPresent(edge.target(), (ignored, degree) -> degree + 1);
        }

        int denominator = Math.max(graph.nodesById().size() - 1, 1);
        List<NodeCentrality> nodes = graph.nodes().stream()
                .map(node -> {
                    int in = inDegree.getOrDefault(node.id(), 0);
                    int out = outDegree.getOrDefault(node.id(), 0);
                    int total = in + out;
                    double centrality = total / (double) denominator;
                    return new NodeCentrality(node, in, out, total, centrality);
                })
                .sorted((left, right) -> {
                    int totalCompare = Integer.compare(right.totalDegree(), left.totalDegree());
                    if (totalCompare != 0) {
                        return totalCompare;
                    }
                    return left.node().id().compareTo(right.node().id());
                })
                .limit(Math.max(limit, 1))
                .toList();

        return new CentralityResult(nodes);
    }

    public record CentralityResult(
            List<NodeCentrality> nodes
    ) {
    }

    public record NodeCentrality(
            GraphNodeView node,
            int inDegree,
            int outDegree,
            int totalDegree,
            double degreeCentrality
    ) {
    }
}
