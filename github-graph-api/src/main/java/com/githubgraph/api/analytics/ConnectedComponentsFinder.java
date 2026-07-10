package com.githubgraph.api.analytics;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class ConnectedComponentsFinder {

    /**
     * Computes connected components over the structural graph without the repository root. Complexity: O(V + E).
     */
    public ComponentsResult findComponents(GraphView graph) {
        Set<String> eligibleNodeIds = graph.nodes().stream()
                .filter(node -> !"repo".equalsIgnoreCase(node.type()))
                .map(GraphNodeView::id)
                .collect(LinkedHashSet::new, LinkedHashSet::add, LinkedHashSet::addAll);

        List<GraphEdgeView> edges = graph.structuralEdgesWithoutRepositoryRoot();
        Set<String> visited = new LinkedHashSet<>();
        List<Component> components = new ArrayList<>();

        for (String nodeId : eligibleNodeIds) {
            if (visited.contains(nodeId)) {
                continue;
            }
            List<GraphNodeView> nodes = bfsComponent(graph, nodeId, edges, visited);
            components.add(new Component("component-" + components.size(), nodes));
        }

        return new ComponentsResult(components);
    }

    private List<GraphNodeView> bfsComponent(
            GraphView graph,
            String startNodeId,
            List<GraphEdgeView> edges,
            Set<String> visited
    ) {
        ArrayDeque<String> queue = new ArrayDeque<>();
        List<GraphNodeView> componentNodes = new ArrayList<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            String current = queue.removeFirst();
            componentNodes.add(graph.node(current));
            for (GraphEdgeView edge : edges) {
                String neighbor = null;
                if (edge.source().equals(current)) {
                    neighbor = edge.target();
                } else if (edge.target().equals(current)) {
                    neighbor = edge.source();
                }
                if (neighbor != null && !visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.addLast(neighbor);
                }
            }
        }

        return componentNodes;
    }

    public record ComponentsResult(
            List<Component> components
    ) {
    }

    public record Component(
            String id,
            List<GraphNodeView> nodes
    ) {
    }
}
