package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TopologicalSorterTest {

    private final TopologicalSorter sorter = new TopologicalSorter();

    @Test
    void returnsOrderForDag() {
        GraphView graph = new GraphView(
                List.of(node("a"), node("b"), node("c")),
                List.of(
                        edge("e1", "a", "b", "CALLS"),
                        edge("e2", "b", "c", "CALLS")
                )
        );

        TopologicalSorter.TopologicalSortResult result = sorter.sort(graph);

        assertTrue(result.acyclic());
        assertEquals(List.of("a", "b", "c"), result.orderedNodes().stream().map(GraphNodeView::id).toList());
    }

    @Test
    void returnsInformativeFailureWhenCycleExists() {
        GraphView graph = new GraphView(
                List.of(node("a"), node("b")),
                List.of(
                        edge("e1", "a", "b", "CALLS"),
                        edge("e2", "b", "a", "CALLS")
                )
        );

        TopologicalSorter.TopologicalSortResult result = sorter.sort(graph);

        assertFalse(result.acyclic());
        assertEquals("Cycles detected in dependency graph", result.message());
    }

    @Test
    void handlesSingleNodeGraph() {
        GraphView graph = new GraphView(List.of(node("solo")), List.of());
        TopologicalSorter.TopologicalSortResult result = sorter.sort(graph);
        assertTrue(result.acyclic());
        assertEquals(List.of("solo"), result.orderedNodes().stream().map(GraphNodeView::id).toList());
    }

    private GraphNodeView node(String id) {
        return new GraphNodeView(id, "function", id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
