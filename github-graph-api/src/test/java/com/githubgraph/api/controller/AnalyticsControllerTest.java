package com.githubgraph.api.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.githubgraph.api.dto.analytics.CycleDetectionResponse;
import com.githubgraph.api.service.GraphAnalyticsService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AnalyticsController.class)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GraphAnalyticsService graphAnalyticsService;

    @Test
    void cyclesEndpointReturnsStructuredResponse() throws Exception {
        when(graphAnalyticsService.detectCycles("repo-123"))
                .thenReturn(new CycleDetectionResponse("repo-123", true, 1, List.of(
                        new CycleDetectionResponse.CyclePath(List.of("a", "b", "a"))
                )));

        mockMvc.perform(get("/api/v1/analytics/cycles").param("repositoryId", "repo-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repositoryId").value("repo-123"))
                .andExpect(jsonPath("$.hasCycles").value(true))
                .andExpect(jsonPath("$.cycles[0].nodeIds[0]").value("a"));
    }
}
