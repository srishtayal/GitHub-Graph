package com.githubgraph.api.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectedComponentsFinderTest {

    private final ConnectedComponentsFinder finder = new ConnectedComponentsFinder();

    @Test
    void findsDisconnectedSubgraphsWithoutRepositoryRootCollapsingThem() {
        GraphView graph = new GraphView(
                List.of(
                        node("repo", "repo"),
                        node("a", "file"),
                        node("b", "function"),
                        node("c", "file"),
                        node("d", "function")
                ),
                List.of(
                        edge("e1", "a", "repo", "BELONGS_TO"),
                        edge("e2", "b", "a", "BELONGS_TO"),
                        edge("e3", "c", "repo", "BELONGS_TO"),
                        edge("e4", "d", "c", "BELONGS_TO")
                )
        );

        ConnectedComponentsFinder.ComponentsResult result = finder.findComponents(graph);

        assertEquals(2, result.components().size());
    }

    @Test
    void returnsEmptyResultForEmptyGraph() {
        ConnectedComponentsFinder.ComponentsResult result = finder.findComponents(new GraphView(List.of(), List.of()));
        assertEquals(0, result.components().size());
    }

    private GraphNodeView node(String id, String type) {
        return new GraphNodeView(id, type, id, Map.of());
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }
}
