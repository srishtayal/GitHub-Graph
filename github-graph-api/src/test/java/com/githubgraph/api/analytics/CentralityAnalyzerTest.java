package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CentralityAnalyzerTest {

    private final CentralityAnalyzer analyzer = new CentralityAnalyzer();

    @Test
    void ranksMostConnectedNodeFirst() {
        GraphView graph = new GraphView(
                List.of(node("a"), node("b"), node("c")),
                List.of(
                        edge("e1", "a", "b", "CALLS"),
                        edge("e2", "a", "c", "CALLS")
                )
        );

        CentralityAnalyzer.CentralityResult result = analyzer.analyze(graph, 3);

        assertEquals("a", result.nodes().getFirst().node().id());
        assertEquals(2, result.nodes().getFirst().totalDegree());
    }

    @Test
    void handlesGraphWithoutEdges() {
        GraphView graph = new GraphView(List.of(node("a")), List.of());
        CentralityAnalyzer.CentralityResult result = analyzer.analyze(graph, 5);
        assertEquals(1, result.nodes().size());
        assertEquals(0, result.nodes().getFirst().totalDegree());
    }

    private GraphNodeView node(String id) {
        return new GraphNodeView(id, "function", id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
