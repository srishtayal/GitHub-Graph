package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DepthFirstTraversalTest {

    private final DepthFirstTraversal traversal = new DepthFirstTraversal();

    @Test
    void tracesDependencyChainInDepthFirstOrder() {
        GraphView graph = new GraphView(
                List.of(node("login"), node("service"), node("db")),
                List.of(
                        edge("e1", "login", "service", "CALLS"),
                        edge("e2", "service", "db", "CALLS")
                )
        );

        DepthFirstTraversal.TraversalResult result = traversal.traceDependencies(graph, "login");

        assertEquals(List.of("login", "service", "db"), result.records().stream().map(record -> record.node().id()).toList());
    }

    private GraphNodeView node(String id) {
        return new GraphNodeView(id, "function", id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
