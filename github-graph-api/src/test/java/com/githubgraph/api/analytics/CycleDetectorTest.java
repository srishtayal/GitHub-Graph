package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CycleDetectorTest {

    private final CycleDetector detector = new CycleDetector();

    @Test
    void detectsSimpleCycle() {
        GraphView graph = new GraphView(
                List.of(node("a"), node("b"), node("c")),
                List.of(
                        edge("e1", "a", "b", "IMPORTS"),
                        edge("e2", "b", "c", "IMPORTS"),
                        edge("e3", "c", "a", "IMPORTS")
                )
        );

        CycleDetector.CycleResult result = detector.detectCycles(graph);

        assertTrue(result.hasCycles());
        assertEquals(1, result.cycles().size());
    }

    @Test
    void returnsNoCyclesForDag() {
        GraphView graph = new GraphView(
                List.of(node("a"), node("b"), node("c")),
                List.of(
                        edge("e1", "a", "b", "IMPORTS"),
                        edge("e2", "b", "c", "IMPORTS")
                )
        );

        CycleDetector.CycleResult result = detector.detectCycles(graph);

        assertEquals(false, result.hasCycles());
        assertEquals(0, result.cycles().size());
    }

    private GraphNodeView node(String id) {
        return new GraphNodeView(id, "module", id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
