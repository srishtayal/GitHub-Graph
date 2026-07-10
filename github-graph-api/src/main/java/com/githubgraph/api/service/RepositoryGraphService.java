package com.githubgraph.api.service;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.dto.AnalysisServiceResponse;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

@Service
public class RepositoryGraphService {

    private static final Pattern RELATIONSHIP_TYPE_PATTERN = Pattern.compile("[A-Z_]+");

    private final Neo4jClient neo4jClient;
    private final ObjectMapper objectMapper;

    public RepositoryGraphService(Neo4jClient neo4jClient, ObjectMapper objectMapper) {
        this.neo4jClient = neo4jClient;
        this.objectMapper = objectMapper;
    }

    public void replaceSnapshotGraph(
            UUID repositoryId,
            RepositorySnapshotEntity snapshot,
            AnalysisServiceResponse.Graph graph
    ) {
        Map<String, Object> parameters = Map.of(
                "repositoryId", repositoryId.toString(),
                "snapshotId", snapshot.getId().toString()
        );
        neo4jClient.query("""
                MATCH (n:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId})
                DETACH DELETE n
                """)
                .bindAll(parameters)
                .run();

        List<Map<String, Object>> nodes = graph.nodes() != null ? graph.nodes() : List.of();
        if (!nodes.isEmpty()) {
            neo4jClient.query("""
                    UNWIND $nodes AS node
                    MERGE (n:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId, id: node.id})
                    SET n.type = node.type,
                        n.label = node.label,
                        n += coalesce(node.properties, {})
                    """)
                    .bind(repositoryId.toString()).to("repositoryId")
                    .bind(snapshot.getId().toString()).to("snapshotId")
                    .bind(nodes).to("nodes")
                    .run();
        }

        List<Map<String, Object>> edges = graph.edges() != null ? graph.edges() : List.of();
        Map<String, List<Map<String, Object>>> edgesByType = new HashMap<>();
        for (Map<String, Object> edge : edges) {
            String relationshipType = sanitizeRelationshipType(String.valueOf(edge.get("type")));
            edgesByType.computeIfAbsent(relationshipType, ignored -> new ArrayList<>()).add(edge);
        }

        for (Map.Entry<String, List<Map<String, Object>>> entry : edgesByType.entrySet()) {
            String cypher = """
                    UNWIND $edges AS edge
                    MATCH (source:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId, id: edge.source})
                    MATCH (target:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId, id: edge.target})
                    MERGE (source)-[r:%s {repositoryId: $repositoryId, snapshotId: $snapshotId, edgeId: edge.id}]->(target)
                    SET r += coalesce(edge.properties, {})
                    """.formatted(entry.getKey());

            neo4jClient.query(cypher)
                    .bind(repositoryId.toString()).to("repositoryId")
                    .bind(snapshot.getId().toString()).to("snapshotId")
                    .bind(entry.getValue()).to("edges")
                    .run();
        }
    }

    public JsonNode loadRepositoryGraph(UUID repositoryId, RepositorySnapshotEntity snapshot) {
        return objectMapper.valueToTree(loadGraphView(repositoryId, snapshot).toPayload());
    }

    public GraphView loadGraphView(UUID repositoryId, RepositorySnapshotEntity snapshot) {
        Map<String, Object> result = neo4jClient.query("""
                CALL {
                    MATCH (n:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId})
                    RETURN collect({
                        id: n.id,
                        type: n.type,
                        label: n.label,
                        properties: properties(n)
                    }) AS nodes
                }
                CALL {
                    MATCH (source:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId})-[r]->(target:CodeGraphNode {repositoryId: $repositoryId, snapshotId: $snapshotId})
                    RETURN collect({
                        id: r.edgeId,
                        source: source.id,
                        target: target.id,
                        type: type(r),
                        properties: properties(r)
                    }) AS edges
                }
                RETURN nodes, edges
                """)
                .bind(repositoryId.toString()).to("repositoryId")
                .bind(snapshot.getId().toString()).to("snapshotId")
                .fetch()
                .one()
                .orElseThrow(() -> new NotFoundException("Repository graph not found"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawNodes = (List<Map<String, Object>>) result.getOrDefault("nodes", List.of());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawEdges = (List<Map<String, Object>>) result.getOrDefault("edges", List.of());

        List<Map<String, Object>> nodes = rawNodes.stream()
                .filter(node -> node.get("id") != null)
                .map(this::sanitizeNode)
                .toList();
        List<Map<String, Object>> edges = rawEdges.stream()
                .filter(edge -> edge != null && edge.get("id") != null)
                .map(this::sanitizeEdge)
                .toList();

        List<GraphNodeView> graphNodes = nodes.stream()
                .map(node -> new GraphNodeView(
                        String.valueOf(node.get("id")),
                        String.valueOf(node.get("type")),
                        String.valueOf(node.get("label")),
                        castMap(node.get("properties"))
                ))
                .toList();
        List<GraphEdgeView> graphEdges = edges.stream()
                .map(edge -> new GraphEdgeView(
                        String.valueOf(edge.get("id")),
                        String.valueOf(edge.get("source")),
                        String.valueOf(edge.get("target")),
                        String.valueOf(edge.get("type")),
                        castMap(edge.get("properties"))
                ))
                .toList();

        return new GraphView(graphNodes, graphEdges);
    }

    private Map<String, Object> sanitizeNode(Map<String, Object> rawNode) {
        Map<String, Object> properties = new LinkedHashMap<>(castMap(rawNode.get("properties")));
        properties.remove("repositoryId");
        properties.remove("snapshotId");
        properties.remove("id");
        properties.remove("type");
        properties.remove("label");

        return Map.of(
                "id", rawNode.get("id"),
                "type", rawNode.get("type"),
                "label", rawNode.get("label"),
                "properties", properties
        );
    }

    private Map<String, Object> sanitizeEdge(Map<String, Object> rawEdge) {
        Map<String, Object> properties = new LinkedHashMap<>(castMap(rawEdge.get("properties")));
        properties.remove("repositoryId");
        properties.remove("snapshotId");
        properties.remove("edgeId");

        return Map.of(
                "id", rawEdge.get("id"),
                "source", rawEdge.get("source"),
                "target", rawEdge.get("target"),
                "type", rawEdge.get("type"),
                "properties", properties
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String sanitizeRelationshipType(String rawType) {
        String candidate = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (!RELATIONSHIP_TYPE_PATTERN.matcher(candidate).matches()) {
            throw new IllegalArgumentException("Invalid graph relationship type: " + rawType);
        }
        return candidate;
    }
}
