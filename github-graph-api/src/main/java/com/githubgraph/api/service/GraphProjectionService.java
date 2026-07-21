package com.githubgraph.api.service;

import com.githubgraph.api.dto.graph.GraphProjectionResponse;
import org.springframework.stereotype.Service;

@Service
public class GraphProjectionService {

    private final GraphLoaderService graphLoaderService;
    private final GraphProjectionEngine projectionEngine;

    public GraphProjectionService(GraphLoaderService graphLoaderService) {
        this.graphLoaderService = graphLoaderService;
        this.projectionEngine = new GraphProjectionEngine();
    }

    public GraphProjectionResponse overview(String repositoryId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return projectionEngine.overview(
                repositoryId,
                loaded.snapshot().getId().toString(),
                loaded.graph()
        );
    }

    public GraphProjectionResponse component(String repositoryId, String componentId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return projectionEngine.component(
                repositoryId,
                loaded.snapshot().getId().toString(),
                loaded.graph(),
                componentId
        );
    }

    public GraphProjectionResponse file(String repositoryId, String fileId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return projectionEngine.file(
                repositoryId,
                loaded.snapshot().getId().toString(),
                loaded.graph(),
                fileId
        );
    }

    public GraphProjectionResponse neighborhood(String repositoryId, String nodeId, int depth) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return projectionEngine.neighborhood(
                repositoryId,
                loaded.snapshot().getId().toString(),
                loaded.graph(),
                nodeId,
                depth
        );
    }
}
