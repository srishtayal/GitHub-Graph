package com.githubgraph.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.dto.graph.GraphProjectionResponse;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.NodeCounts;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectedNode;
import com.githubgraph.api.dto.graph.GraphProjectionResponse.ProjectionTotals;
import com.githubgraph.api.service.GraphProjectionService;
import com.githubgraph.api.service.IngestionOrchestratorService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RepositoryController.class)
class RepositoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IngestionOrchestratorService ingestionService;

    @MockBean
    private GraphProjectionService graphProjectionService;

    @Test
    void getGraphReturnsGraphPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode graph = objectMapper.readTree("""
                {"nodes":[{"id":"repo:1","type":"repo","label":"project"}],"edges":[{"id":"edge:1","source":"repo:1","target":"file:1","type":"BELONGS_TO"}]}
                """);

        when(ingestionService.getRepositoryGraph("repo-123")).thenReturn(graph);

        mockMvc.perform(get("/api/v1/repositories/repo-123/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nodes[0].id").value("repo:1"))
                .andExpect(jsonPath("$.edges[0].type").value("BELONGS_TO"));
    }

    @Test
    void getAnalyticsReturnsInsightsPayload() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode analytics = objectMapper.readTree("""
                {"insights":{"most_critical_functions":[]},"connected_components":{"total_components":1}}
                """);

        when(ingestionService.getRepositoryAnalytics("repo-123", null, 10)).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/repositories/repo-123/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.insights.most_critical_functions").isArray())
                .andExpect(jsonPath("$.connected_components.total_components").value(1));
    }

    @Test
    void getAnalyticsWithNodeReturnsNodeAnalysis() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode analytics = objectMapper.readTree("""
                {"node_analysis":{"node_id":"file:a"},"insights":{"selected_node":{"id":"file:a"}}}
                """);

        when(ingestionService.getRepositoryAnalytics("repo-123", "a.py", 5)).thenReturn(analytics);

        mockMvc.perform(get("/api/v1/repositories/repo-123/analytics")
                        .param("nodeId", "a.py")
                        .param("maxDepth", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.node_analysis.node_id").value("file:a"))
                .andExpect(jsonPath("$.insights.selected_node.id").value("file:a"));
    }

    @Test
    void getGraphOverviewReturnsProjectionWithoutRawGraphPayload() throws Exception {
        GraphProjectionResponse response = projection("OVERVIEW", "repo:1", "component:core");
        when(graphProjectionService.overview("repo-123")).thenReturn(response);

        mockMvc.perform(get("/api/v1/repositories/repo-123/graph/views/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.snapshotId").value("snapshot-1"))
                .andExpect(jsonPath("$.level").value("OVERVIEW"))
                .andExpect(jsonPath("$.nodes[0].id").value("component:core"))
                .andExpect(jsonPath("$.totals.rawNodeCount").value(300))
                .andExpect(jsonPath("$.nodes[0].underlyingNodeIds[0]").value("file:1"));
    }

    @Test
    void graphProjectionRoutesPassPathAndDepthParameters() throws Exception {
        when(graphProjectionService.component("repo-123", "component:core"))
                .thenReturn(projection("COMPONENT", "component:core", "file:1"));
        when(graphProjectionService.file("repo-123", "file:1"))
                .thenReturn(projection("FILE", "file:1", "function:1"));
        when(graphProjectionService.neighborhood("repo-123", "function:1", 3))
                .thenReturn(projection("NEIGHBORHOOD", "function:1", "function:1"));

        mockMvc.perform(get("/api/v1/repositories/repo-123/graph/views/components/component:core"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("COMPONENT"));
        mockMvc.perform(get("/api/v1/repositories/repo-123/graph/views/files/file:1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("FILE"));
        mockMvc.perform(get("/api/v1/repositories/repo-123/graph/neighborhood/function:1").param("depth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value("NEIGHBORHOOD"));
    }

    private GraphProjectionResponse projection(String level, String rootId, String nodeId) {
        return new GraphProjectionResponse(
                "repo-123",
                "snapshot-1",
                level,
                rootId,
                15,
                false,
                new ProjectionTotals(300, 500, 1, 0),
                List.of(new ProjectedNode(
                        nodeId,
                        "Core",
                        level,
                        "source",
                        new NodeCounts(1, 2, 3, 0),
                        1,
                        2,
                        0.5,
                        6,
                        List.of(),
                        List.of("file:1"),
                        true
                )),
                List.of()
        );
    }
}
