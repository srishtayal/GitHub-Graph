package com.githubgraph.api.controller;

import com.githubgraph.api.dto.analytics.ConnectedComponentsResponse;
import com.githubgraph.api.dto.analytics.CriticalNodesResponse;
import com.githubgraph.api.dto.analytics.CycleDetectionResponse;
import com.githubgraph.api.dto.analytics.DependencyPathResponse;
import com.githubgraph.api.dto.analytics.ImpactAnalysisResponse;
import com.githubgraph.api.dto.analytics.TopologicalOrderResponse;
import com.githubgraph.api.service.GraphAnalyticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final GraphAnalyticsService graphAnalyticsService;

    public AnalyticsController(GraphAnalyticsService graphAnalyticsService) {
        this.graphAnalyticsService = graphAnalyticsService;
    }

    @GetMapping("/impact/{nodeId}")
    public ImpactAnalysisResponse analyzeImpact(
            @PathVariable String nodeId,
            @RequestParam String repositoryId
    ) {
        return graphAnalyticsService.analyzeImpact(repositoryId, nodeId);
    }

    @GetMapping("/path/{nodeId}")
    public DependencyPathResponse traceDependencies(
            @PathVariable String nodeId,
            @RequestParam String repositoryId
    ) {
        return graphAnalyticsService.traceDependencies(repositoryId, nodeId);
    }

    @GetMapping("/components")
    public ConnectedComponentsResponse getConnectedComponents(@RequestParam String repositoryId) {
        return graphAnalyticsService.getConnectedComponents(repositoryId);
    }

    @GetMapping("/cycles")
    public CycleDetectionResponse detectCycles(@RequestParam String repositoryId) {
        return graphAnalyticsService.detectCycles(repositoryId);
    }

    @GetMapping("/topological-order")
    public TopologicalOrderResponse getTopologicalOrder(@RequestParam String repositoryId) {
        return graphAnalyticsService.getTopologicalOrder(repositoryId);
    }

    @GetMapping("/critical")
    public CriticalNodesResponse getCriticalNodes(
            @RequestParam String repositoryId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return graphAnalyticsService.findCriticalNodes(repositoryId, limit);
    }
}
