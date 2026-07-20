package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.analytics.model.GraphEdgeView;
import com.githubgraph.api.analytics.model.GraphNodeView;
import com.githubgraph.api.analytics.model.GraphView;
import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class IntelligenceServiceContractTest {

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void serializesGraphPayloadAndReturnsPhaseSixSimilarityContract() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        AtomicReference<JsonNode> receivedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/v1/intelligence/similarity", exchange -> {
            receivedRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = """
                    {
                      "targetNodeId": "function:login",
                      "nodeType": "function",
                      "results": [{
                        "targetNodeId": "function:login",
                        "candidateNodeId": "function:validate",
                        "nodeType": "function",
                        "score": 0.75,
                        "featureScores": {},
                        "clusterId": null
                      }]
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        UUID repositoryId = UUID.randomUUID();
        RepositoryEntity repository = new RepositoryEntity();
        repository.setId(repositoryId);
        RepositorySnapshotEntity snapshot = new RepositorySnapshotEntity();
        snapshot.setRepository(repository);
        snapshot.prePersist();
        UUID snapshotId = snapshot.getId();

        GraphView neo4jGraph = new GraphView(
                java.util.List.of(
                        new GraphNodeView("function:login", "function", "login", Map.of()),
                        new GraphNodeView("function:validate", "function", "validate", Map.of())
                ),
                java.util.List.of(
                        new GraphEdgeView(
                                "edge:calls",
                                "function:login",
                                "function:validate",
                                "CALLS",
                                Map.of()
                        )
                )
        );
        GraphLoaderService graphLoaderService = mock(GraphLoaderService.class);
        when(graphLoaderService.loadGraph(repositoryId.toString(), snapshotId.toString()))
                .thenReturn(new GraphLoaderService.LoadedGraph(repository, snapshot, neo4jGraph));

        FailureHistoryService failureHistoryService = mock(FailureHistoryService.class);
        AppProperties properties = new AppProperties(
                new AppProperties.Analysis("http://localhost:" + server.getAddress().getPort()),
                new AppProperties.CloneProperties("/tmp/repos", 120, 1024, 100),
                new AppProperties.Github("https://api.github.com", ""),
                new AppProperties.Neo4jInitialization(3, 1, 4)
        );
        IntelligenceClientService client = new IntelligenceClientService(properties, objectMapper);
        IntelligenceService service = new IntelligenceService(
                graphLoaderService,
                failureHistoryService,
                client
        );

        JsonNode response = service.similarity(
                repositoryId.toString(),
                snapshotId.toString(),
                "function:login",
                5
        );

        assertEquals(0.75, response.path("results").get(0).path("score").asDouble());
        assertEquals("function:login", receivedRequest.get().path("targetNodeId").asText());
        assertEquals(2, receivedRequest.get().path("graph").path("nodes").size());
        assertEquals("CALLS", receivedRequest.get().path("graph").path("edges").get(0).path("type").asText());
        assertEquals(5, receivedRequest.get().path("configuration").path("limit").asInt());
    }
}
