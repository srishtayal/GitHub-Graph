package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.dto.explanation.GroundedExplanationRequest;
import com.githubgraph.api.exception.BadGatewayException;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ExplanationQueryServiceContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void loadsGraphAndHistoryForMinimalPublicQuery() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<JsonNode> receivedRequest = new AtomicReference<>();
        startServer("/internal/v1/explanations/query", exchange -> {
            receivedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] body = groundedResponse().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        RepositoryEntity repository = repository();
        RepositorySnapshotEntity snapshot = snapshot(repository);
        GraphView graph = new GraphView(
                List.of(new GraphNodeView("file:api", "file", "api.py", Map.of())),
                List.of()
        );
        GraphLoaderService graphLoader = mock(GraphLoaderService.class);
        GraphLoaderService.LoadedGraph loaded = new GraphLoaderService.LoadedGraph(repository, snapshot, graph);
        when(graphLoader.loadLatestGraph(repository.getId().toString())).thenReturn(loaded);
        FailureHistoryService history = mock(FailureHistoryService.class);
        when(history.historicalFailures(loaded)).thenReturn(List.of(Map.of(
                "failureId", "failure-one",
                "repositoryId", repository.getId().toString()
        )));

        ExplanationQueryService service = new ExplanationQueryService(
                graphLoader,
                history,
                client(objectMapper)
        );
        JsonNode response = service.query(new GroundedExplanationRequest(
                repository.getId().toString(),
                "What is the repository structure?",
                null,
                null,
                null
        ));

        assertEquals("repository_structure", response.path("intent").asText());
        assertEquals(1, receivedRequest.get().path("graph").path("nodes").size());
        assertEquals(1, receivedRequest.get().path("history").size());
        assertEquals(snapshot.getId().toString(), receivedRequest.get()
                .path("snapshotMetadata").path("snapshotId").asText());
        assertEquals("What is the repository structure?", receivedRequest.get().path("query").asText());
    }

    @Test
    void preservesProviderFailureAsBadGateway() throws Exception {
        startServer("/internal/v1/explanations/query", exchange -> {
            byte[] body = "{\"detail\":\"provider failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(502, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertThrows(
                BadGatewayException.class,
                () -> client(new ObjectMapper()).queryExplanation(Map.of("query", "question"))
        );
    }

    private void startServer(String path, com.sun.net.httpserver.HttpHandler handler) throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext(path, handler);
        server.start();
    }

    private IntelligenceClientService client(ObjectMapper objectMapper) {
        AppProperties properties = new AppProperties(
                new AppProperties.Analysis("http://localhost:" + server.getAddress().getPort()),
                new AppProperties.CloneProperties("/tmp/repos", 120, 1024, 100),
                new AppProperties.Github("https://api.github.com", ""),
                new AppProperties.Neo4jInitialization(3, 1, 4)
        );
        return new IntelligenceClientService(properties, objectMapper);
    }

    private RepositoryEntity repository() {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setId(UUID.randomUUID());
        repository.setGithubUrl("https://github.com/example/sample");
        repository.setOwner("example");
        repository.setName("sample");
        repository.setDefaultBranch("main");
        repository.setPublic(true);
        repository.setStatus("READY");
        repository.prePersist();
        return repository;
    }

    private RepositorySnapshotEntity snapshot(RepositoryEntity repository) {
        RepositorySnapshotEntity snapshot = new RepositorySnapshotEntity();
        snapshot.setRepository(repository);
        snapshot.setBranchName("main");
        snapshot.setCommitSha("abc123");
        snapshot.setRootDirectory("/workspace/repos/sample");
        snapshot.setTotalFiles(1);
        snapshot.setTotalDirectories(1);
        snapshot.setLanguageSummaryJson("{\"Python\":1}");
        snapshot.prePersist();
        return snapshot;
    }

    private String groundedResponse() {
        return """
                {
                  "intent": "repository_structure",
                  "answer": "The repository contains one file.",
                  "supportingEvidence": [{
                    "evidenceId": "graph:summary",
                    "sourceType": "graph",
                    "rationale": "Graph counts."
                  }],
                  "referencedNodeIds": ["file:api"],
                  "referencedEdgeIds": [],
                  "confidence": "high",
                  "limitations": [],
                  "followUpSuggestions": [],
                  "snapshotMetadata": {
                    "repositoryId": "repo",
                    "snapshotId": "snapshot"
                  },
                  "modelMetadata": {
                    "provider": "gemini",
                    "model": "test",
                    "promptVersion": "test",
                    "orchestrationVersion": "test"
                  }
                }
                """;
    }
}
