package com.githubgraph.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.service.IngestionOrchestratorService;
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
}