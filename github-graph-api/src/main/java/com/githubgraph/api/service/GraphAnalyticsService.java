package com.githubgraph.api.service;

import com.githubgraph.api.analytics.BreadthFirstTraversal;
import com.githubgraph.api.analytics.CentralityAnalyzer;
import com.githubgraph.api.analytics.ConnectedComponentsFinder;
import com.githubgraph.api.analytics.CycleDetector;
import com.githubgraph.api.analytics.DepthFirstTraversal;
import com.githubgraph.api.analytics.TopologicalSorter;
import com.githubgraph.api.dto.analytics.ConnectedComponentsResponse;
import com.githubgraph.api.dto.analytics.CriticalNodesResponse;
import com.githubgraph.api.dto.analytics.CycleDetectionResponse;
import com.githubgraph.api.dto.analytics.DependencyPathResponse;
import com.githubgraph.api.dto.analytics.ImpactAnalysisResponse;
import com.githubgraph.api.dto.analytics.TopologicalOrderResponse;
import org.springframework.stereotype.Service;

@Service
public class GraphAnalyticsService {

    private final GraphLoaderService graphLoaderService;
    private final BreadthFirstTraversal breadthFirstTraversal = new BreadthFirstTraversal();
    private final DepthFirstTraversal depthFirstTraversal = new DepthFirstTraversal();
    private final ConnectedComponentsFinder connectedComponentsFinder = new ConnectedComponentsFinder();
    private final CycleDetector cycleDetector = new CycleDetector();
    private final TopologicalSorter topologicalSorter = new TopologicalSorter();
    private final CentralityAnalyzer centralityAnalyzer = new CentralityAnalyzer();

    public GraphAnalyticsService(GraphLoaderService graphLoaderService) {
        this.graphLoaderService = graphLoaderService;
    }

    public ImpactAnalysisResponse analyzeImpact(String repositoryId, String nodeId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return ImpactAnalysisResponse.from(repositoryId, breadthFirstTraversal.traverseImpact(loaded.graph(), nodeId));
    }

    public DependencyPathResponse traceDependencies(String repositoryId, String nodeId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return DependencyPathResponse.from(repositoryId, depthFirstTraversal.traceDependencies(loaded.graph(), nodeId));
    }

    public ConnectedComponentsResponse getConnectedComponents(String repositoryId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return ConnectedComponentsResponse.from(repositoryId, connectedComponentsFinder.findComponents(loaded.graph()));
    }

    public CycleDetectionResponse detectCycles(String repositoryId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return CycleDetectionResponse.from(repositoryId, cycleDetector.detectCycles(loaded.graph()));
    }

    public TopologicalOrderResponse getTopologicalOrder(String repositoryId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return TopologicalOrderResponse.from(repositoryId, topologicalSorter.sort(loaded.graph()));
    }

    public CriticalNodesResponse findCriticalNodes(String repositoryId, int limit) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(repositoryId);
        return CriticalNodesResponse.from(repositoryId, centralityAnalyzer.analyze(loaded.graph(), limit));
    }
}
