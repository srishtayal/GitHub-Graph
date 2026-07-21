package com.githubgraph.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.FileSummaryResponse;
import com.githubgraph.api.dto.ImportSummaryResponse;
import com.githubgraph.api.dto.RepositorySummaryResponse;
import com.githubgraph.api.dto.SymbolSummaryResponse;
import com.githubgraph.api.dto.graph.GraphProjectionResponse;
import com.githubgraph.api.service.GraphProjectionService;
import com.githubgraph.api.service.IngestionOrchestratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

    private final IngestionOrchestratorService ingestionService;
    private final GraphProjectionService graphProjectionService;

    public RepositoryController(
            IngestionOrchestratorService ingestionService,
            GraphProjectionService graphProjectionService
    ) {
        this.ingestionService = ingestionService;
        this.graphProjectionService = graphProjectionService;
    }

    @GetMapping("/{repositoryId}")
    public RepositorySummaryResponse getRepository(@PathVariable String repositoryId) {
        return ingestionService.getRepositorySummary(repositoryId);
    }

    @GetMapping("/{repositoryId}/files")
    public FileSummaryResponse getFiles(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryFiles(repositoryId);
    }

    @GetMapping("/{repositoryId}/symbols")
    public SymbolSummaryResponse getSymbols(@PathVariable String repositoryId) {
        return ingestionService.getRepositorySymbols(repositoryId);
    }

    @GetMapping("/{repositoryId}/imports")
    public ImportSummaryResponse getImports(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryImports(repositoryId);
    }

    @GetMapping("/{repositoryId}/analysis")
    public JsonNode getAnalysis(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryAnalysis(repositoryId);
    }

    @GetMapping("/{repositoryId}/graph")
    public JsonNode getGraph(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryGraph(repositoryId);
    }

    @GetMapping("/{repositoryId}/graph/views/overview")
    public GraphProjectionResponse getGraphOverview(@PathVariable String repositoryId) {
        return graphProjectionService.overview(repositoryId);
    }

    @GetMapping("/{repositoryId}/graph/views/components/{componentId}")
    public GraphProjectionResponse getGraphComponent(
            @PathVariable String repositoryId,
            @PathVariable String componentId
    ) {
        return graphProjectionService.component(repositoryId, componentId);
    }

    @GetMapping("/{repositoryId}/graph/views/files/{fileId}")
    public GraphProjectionResponse getGraphFile(
            @PathVariable String repositoryId,
            @PathVariable String fileId
    ) {
        return graphProjectionService.file(repositoryId, fileId);
    }

    @GetMapping("/{repositoryId}/graph/neighborhood/{nodeId}")
    public GraphProjectionResponse getGraphNeighborhood(
            @PathVariable String repositoryId,
            @PathVariable String nodeId,
            @RequestParam(required = false, defaultValue = "2") int depth
    ) {
        return graphProjectionService.neighborhood(repositoryId, nodeId, depth);
    }

    @GetMapping("/{repositoryId}/analytics")
    public JsonNode getAnalytics(
            @PathVariable String repositoryId,
            @RequestParam(required = false) String nodeId,
            @RequestParam(required = false, defaultValue = "10") Integer maxDepth
    ) {
        return ingestionService.getRepositoryAnalytics(repositoryId, nodeId, maxDepth);
    }
}
