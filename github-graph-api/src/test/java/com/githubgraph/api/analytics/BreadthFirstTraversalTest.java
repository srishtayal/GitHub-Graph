package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BreadthFirstTraversalTest {

    private final BreadthFirstTraversal traversal = new BreadthFirstTraversal();

    @Test
    void impactTraversalUsesIncomingDependencyEdges() {
        GraphView graph = new GraphView(
                List.of(
                        node("a"), node("b"), node("c")
                ),
                List.of(
                        edge("e1", "a", "b", "CALLS"),
                        edge("e2", "b", "c", "CALLS")
                )
        );

        BreadthFirstTraversal.TraversalResult result = traversal.traverseImpact(graph, "c");

        assertEquals(List.of("c", "b", "a"), result.records().stream().map(record -> record.node().id()).toList());
    }

    @Test
    void impactTraversalRejectsUnknownNodeId() {
        GraphView graph = new GraphView(List.of(node("a")), List.of());
        assertThrows(IllegalArgumentException.class, () -> traversal.traverseImpact(graph, "missing"));
    }

    @Test
    void impactTraversalHandlesLargeGraphInBreadthFirstOrder() {
        List<GraphNodeView> nodes = java.util.stream.IntStream.range(0, 100)
                .mapToObj(index -> node("n" + index))
                .toList();
        List<GraphEdgeView> edges = java.util.stream.IntStream.range(0, 99)
                .mapToObj(index -> edge("e" + index, "n" + index, "n" + (index + 1), "CALLS"))
                .toList();

        BreadthFirstTraversal.TraversalResult result = traversal.traverseImpact(new GraphView(nodes, edges), "n99");

        assertEquals(100, result.records().size());
        assertEquals("n0", result.records().getLast().node().id());
    }

    private GraphNodeView node(String id) {
        return new GraphNodeView(id, "function", id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
