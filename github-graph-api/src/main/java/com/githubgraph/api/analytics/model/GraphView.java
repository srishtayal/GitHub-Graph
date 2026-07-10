package com.githubgraph.api.analytics.model;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GraphView {

    public static final Set<String> DEPENDENCY_EDGE_TYPES = Set.of(
            "IMPORTS",
            "CALLS",
            "USES",
            "EXTENDS",
            "ROUTE_DEFINES"
    );

    public static final Set<String> CONTAINMENT_EDGE_TYPES = Set.of("CONTAINS");

    private final Map<String, GraphNodeView> nodesById;
    private final List<GraphEdgeView> edges;
    private final Map<String, List<GraphEdgeView>> outgoingEdges;
    private final Map<String, List<GraphEdgeView>> incomingEdges;

    public GraphView(Collection<GraphNodeView> nodes, Collection<GraphEdgeView> edges) {
        this.nodesById = nodes.stream().collect(Collectors.toMap(
                GraphNodeView::id,
                node -> node,
                (left, right) -> right,
                LinkedHashMap::new
        ));
        this.edges = List.copyOf(edges);
        this.outgoingEdges = buildAdjacency(edges, true);
        this.incomingEdges = buildAdjacency(edges, false);
    }

    public Map<String, GraphNodeView> nodesById() {
        return Collections.unmodifiableMap(nodesById);
    }

    public Collection<GraphNodeView> nodes() {
        return Collections.unmodifiableCollection(nodesById.values());
    }

    public List<GraphEdgeView> edges() {
        return Collections.unmodifiableList(edges);
    }

    public GraphNodeView node(String nodeId) {
        return nodesById.get(nodeId);
    }

    public boolean containsNode(String nodeId) {
        return nodesById.containsKey(nodeId);
    }

    public List<GraphEdgeView> outgoingEdges(String nodeId) {
        return outgoingEdges.getOrDefault(nodeId, List.of());
    }

    public List<GraphEdgeView> incomingEdges(String nodeId) {
        return incomingEdges.getOrDefault(nodeId, List.of());
    }

    public List<GraphEdgeView> outgoingDependencyEdges(String nodeId) {
        return outgoingEdges(nodeId).stream().filter(GraphEdgeView::isDependencyEdge).toList();
    }

    public List<GraphEdgeView> incomingDependencyEdges(String nodeId) {
        return incomingEdges(nodeId).stream().filter(GraphEdgeView::isDependencyEdge).toList();
    }

    public List<GraphEdgeView> dependencyEdges() {
        return edges.stream().filter(GraphEdgeView::isDependencyEdge).toList();
    }

    public List<GraphEdgeView> structuralEdgesWithoutRepositoryRoot() {
        return edges.stream()
                .filter(edge -> {
                    GraphNodeView source = node(edge.source());
                    GraphNodeView target = node(edge.target());
                    if (source == null || target == null) {
                        return false;
                    }
                    if ("repo".equalsIgnoreCase(source.type()) || "repo".equalsIgnoreCase(target.type())) {
                        return false;
                    }
                    return edge.isDependencyEdge() || edge.isContainmentEdge();
                })
                .toList();
    }

    public GraphPayloadView toPayload() {
        List<Map<String, Object>> payloadNodes = nodes().stream()
                .map(node -> Map.of(
                        "id", node.id(),
                        "type", node.type(),
                        "label", node.label(),
                        "properties", node.properties()
                ))
                .toList();

        List<Map<String, Object>> payloadEdges = edges().stream()
                .map(edge -> Map.of(
                        "id", edge.id(),
                        "source", edge.source(),
                        "target", edge.target(),
                        "type", edge.type(),
                        "properties", edge.properties()
                ))
                .toList();

        return new GraphPayloadView(payloadNodes, payloadEdges);
    }

    private Map<String, List<GraphEdgeView>> buildAdjacency(Collection<GraphEdgeView> graphEdges, boolean outgoing) {
        Map<String, List<GraphEdgeView>> adjacency = new LinkedHashMap<>();
        for (GraphEdgeView edge : graphEdges) {
            String key = outgoing ? edge.source() : edge.target();
            adjacency.computeIfAbsent(key, ignored -> new java.util.ArrayList<>()).add(edge);
        }
        adjacency.replaceAll((ignored, value) -> List.copyOf(value));
        return adjacency;
    }

    public record GraphPayloadView(
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges
    ) {
    }
}
