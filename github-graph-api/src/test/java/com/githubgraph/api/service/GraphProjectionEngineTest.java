package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import com.githubgraph.api.dto.graph.GraphProjectionResponse;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectedEdge;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectedNode;
import com.githubgraph.api.exception.NotFoundException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GraphProjectionEngineTest {

    private static final String REPOSITORY_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SNAPSHOT_ID = "00000000-0000-0000-0000-000000000002";

    private GraphProjectionEngine engine;
    private GraphView graph;

    @BeforeEach
    void setUp() {
        engine = new GraphProjectionEngine();
        graph = fixtureGraph();
    }

    @Test
    void overviewUsesStableComponentsAndTraceableAggregatedEdges() {
        GraphProjectionResponse first = engine.overview(REPOSITORY_ID, SNAPSHOT_ID, graph);
        GraphProjectionResponse second = engine.overview(REPOSITORY_ID, SNAPSHOT_ID, graph);

        assertEquals(first, second);
        assertEquals("OVERVIEW", first.level());
        assertTrue(first.nodes().size() <= GraphProjectionEngine.OVERVIEW_MAX_NODES);

        ProjectedNode core = nodeNamed(first, "ItsDangerous Core");
        ProjectedNode testing = nodeNamed(first, "Testing");
        ProjectedNode dependencies = nodeNamed(first, "External Dependencies");
        assertEquals(2, core.counts().files());
        assertEquals(2, core.counts().classes());
        assertEquals(3, core.counts().functions());
        assertTrue(core.expandable());
        assertTrue(core.underlyingNodeIds().contains("file-core"));

        ProjectedEdge testCalls = edge(first, testing.id(), core.id());
        assertEquals(2, testCalls.totalRelationshipCount());
        assertEquals(2, testCalls.countsByType().get("CALLS"));
        assertEquals(List.of("edge-test-core", "edge-test-signer"), testCalls.underlyingEdgeIds());

        ProjectedEdge imports = edge(first, core.id(), dependencies.id());
        assertEquals(1, imports.countsByType().get("IMPORTS"));
        assertEquals(List.of("edge-import-hashlib"), imports.underlyingEdgeIds());
        assertFalse(first.edges().stream().anyMatch(edge -> edge.countsByType().containsKey("BELONGS_TO")));
        assertEquals(graph.nodes().size(), first.totals().rawNodeCount());
        assertEquals(graph.edges().size(), first.totals().rawEdgeCount());
    }

    @Test
    void componentProjectsFilesAndModulesWithCrossFileRelationships() {
        GraphProjectionResponse overview = engine.overview(REPOSITORY_ID, SNAPSHOT_ID, graph);
        String coreComponentId = nodeNamed(overview, "ItsDangerous Core").id();

        GraphProjectionResponse component = engine.component(REPOSITORY_ID, SNAPSHOT_ID, graph, coreComponentId);

        assertEquals("COMPONENT", component.level());
        assertEquals(coreComponentId, component.rootId());
        assertTrue(component.nodes().stream().anyMatch(node -> node.id().equals("file-core")));
        assertTrue(component.nodes().stream().anyMatch(node -> node.id().equals("file-signer")));
        assertTrue(component.nodes().stream().anyMatch(node -> node.id().equals("module-hashlib")));

        ProjectedEdge sourceRelationship = edge(component, "file-signer", "file-core");
        assertEquals(2, sourceRelationship.totalRelationshipCount());
        assertEquals(1, sourceRelationship.countsByType().get("CALLS"));
        assertEquals(1, sourceRelationship.countsByType().get("INHERITS"));
        assertTrue(component.nodes().stream()
                .filter(node -> node.id().equals("file-signer"))
                .findFirst()
                .orElseThrow()
                .outgoingDependencyCount() >= 2);
    }

    @Test
    void fileProjectionContainsSymbolsAndRetainsMeaningfulContainment() {
        GraphProjectionResponse file = engine.file(REPOSITORY_ID, SNAPSHOT_ID, graph, "file-signer");

        assertEquals("FILE", file.level());
        assertEquals("file-signer", file.rootId());
        assertEquals(3, file.nodes().size());
        assertTrue(file.nodes().stream().allMatch(node -> node.counts().files() == 0));
        ProjectedEdge containment = edge(file, "function-sign", "class-signer");
        assertEquals(1, containment.countsByType().get("BELONGS_TO"));
        assertEquals(List.of("edge-sign-class"), containment.underlyingEdgeIds());
    }

    @Test
    void neighborhoodReturnsOnlyNodesWithinRequestedDependencyDepth() {
        GraphProjectionResponse depthOne = engine.neighborhood(
                REPOSITORY_ID, SNAPSHOT_ID, graph, "function-test", 1
        );

        assertEquals("NEIGHBORHOOD", depthOne.level());
        assertEquals("function-test", depthOne.rootId());
        assertEquals(
                List.of("function-core", "function-signer", "function-test"),
                depthOne.nodes().stream().map(ProjectedNode::id).sorted().toList()
        );
        assertFalse(depthOne.nodes().stream().anyMatch(node -> node.id().equals("file-doc")));
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.neighborhood(REPOSITORY_ID, SNAPSHOT_ID, graph, "function-test", 6)
        );
        assertThrows(
                NotFoundException.class,
                () -> engine.neighborhood(REPOSITORY_ID, SNAPSHOT_ID, graph, "missing", 1)
        );
    }

    @Test
    void overviewRecognizesTopLevelPythonPackagesDeterministically() {
        GraphView packageGraph = new GraphView(
                List.of(
                        node("repo-package", "repo", "sample", Map.of()),
                        file("file-init", "my_package/__init__.py"),
                        file("file-service", "my_package/service.py"),
                        node("function-run", "function", "run", path("my_package/service.py"))
                ),
                List.of(edge("belongs-run", "function-run", "file-service", "BELONGS_TO"))
        );

        GraphProjectionResponse overview = engine.overview(REPOSITORY_ID, SNAPSHOT_ID, packageGraph);

        ProjectedNode packageNode = nodeNamed(overview, "My Package Core");
        assertEquals(2, packageNode.counts().files());
        assertEquals(1, packageNode.counts().functions());
    }

    private ProjectedNode nodeNamed(GraphProjectionResponse response, String displayName) {
        ProjectedNode node = response.nodes().stream()
                .filter(candidate -> candidate.displayName().equals(displayName))
                .findFirst()
                .orElse(null);
        assertNotNull(node, "Expected projected node " + displayName);
        return node;
    }

    private ProjectedEdge edge(GraphProjectionResponse response, String source, String target) {
        ProjectedEdge edge = response.edges().stream()
                .filter(candidate -> candidate.source().equals(source) && candidate.target().equals(target))
                .findFirst()
                .orElse(null);
        assertNotNull(edge, "Expected projected edge " + source + " -> " + target);
        return edge;
    }

    private GraphView fixtureGraph() {
        List<GraphNodeView> nodes = List.of(
                node("repo", "repo", "itsdangerous", Map.of()),
                file("file-core", "src/itsdangerous/core.py"),
                file("file-signer", "src/itsdangerous/signer.py"),
                file("file-test", "tests/test_signer.py"),
                file("file-doc", "docs/index.rst"),
                file("file-ci", ".github/workflows/ci.yml"),
                file("file-readme", "README.md"),
                node("class-core", "class", "BaseSigner", path("src/itsdangerous/core.py")),
                node("class-signer", "class", "Signer", path("src/itsdangerous/signer.py")),
                node("function-core", "function", "derive_key", path("src/itsdangerous/core.py")),
                node("function-signer", "function", "Signer.sign", path("src/itsdangerous/signer.py")),
                node("function-sign", "function", "Signer.get_signature", path("src/itsdangerous/signer.py")),
                node("function-test", "function", "test_signer", path("tests/test_signer.py")),
                node("module-hashlib", "module", "hashlib", Map.of("name", "hashlib"))
        );
        List<GraphEdgeView> edges = List.of(
                edge("belongs-core", "class-core", "file-core", "BELONGS_TO"),
                edge("belongs-signer", "class-signer", "file-signer", "BELONGS_TO"),
                edge("edge-sign-class", "function-sign", "class-signer", "BELONGS_TO"),
                edge("belongs-core-function", "function-core", "file-core", "BELONGS_TO"),
                edge("belongs-signer-function", "function-signer", "class-signer", "BELONGS_TO"),
                edge("belongs-test-function", "function-test", "file-test", "BELONGS_TO"),
                edge("edge-signer-core", "function-signer", "function-core", "CALLS"),
                edge("edge-test-signer", "function-test", "function-signer", "CALLS"),
                edge("edge-test-core", "function-test", "function-core", "CALLS"),
                edge("edge-inherits", "class-signer", "class-core", "INHERITS"),
                edge("edge-import-hashlib", "file-core", "module-hashlib", "IMPORTS")
        );
        return new GraphView(nodes, edges);
    }

    private GraphNodeView file(String id, String relativePath) {
        return node(id, "file", relativePath, path(relativePath));
    }

    private GraphNodeView node(String id, String type, String label, Map<String, Object> properties) {
        return new GraphNodeView(id, type, label, properties);
    }

    private GraphEdgeView edge(String id, String source, String target, String type) {
        return new GraphEdgeView(id, source, target, type, Map.of());
    }

    private Map<String, Object> path(String relativePath) {
        return Map.of("relativePath", relativePath);
    }
}
