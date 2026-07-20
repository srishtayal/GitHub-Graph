package com.githubgraph.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.intelligence.CreateFailureRequest;
import com.githubgraph.api.dto.intelligence.FailureLocalizationRequest;
import com.githubgraph.api.dto.intelligence.FailureRecordResponse;
import com.githubgraph.api.exception.ValidationException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class IntelligenceService {

    private final GraphLoaderService graphLoaderService;
    private final FailureHistoryService failureHistoryService;
    private final IntelligenceClientService intelligenceClientService;

    public IntelligenceService(
            GraphLoaderService graphLoaderService,
            FailureHistoryService failureHistoryService,
            IntelligenceClientService intelligenceClientService
    ) {
        this.graphLoaderService = graphLoaderService;
        this.failureHistoryService = failureHistoryService;
        this.intelligenceClientService = intelligenceClientService;
    }

    public JsonNode similarity(
            String repositoryId,
            String snapshotId,
            String nodeId,
            int limit
    ) {
        if (limit < 0) {
            throw new ValidationException("INVALID_SIMILARITY_LIMIT", "Similarity limit cannot be negative");
        }
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(repositoryId, snapshotId);
        Map<String, Object> request = Map.of(
                "graph", loaded.graph().toPayload(),
                "targetNodeId", nodeId,
                "configuration", Map.of("limit", limit)
        );
        return intelligenceClientService.rankSimilarity(request);
    }

    public JsonNode clusters(
            String repositoryId,
            String snapshotId,
            String nodeType,
            double threshold
    ) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new ValidationException(
                    "INVALID_SIMILARITY_THRESHOLD",
                    "Similarity threshold must be between 0.0 and 1.0"
            );
        }
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(repositoryId, snapshotId);
        Map<String, Object> request = Map.of(
                "graph", loaded.graph().toPayload(),
                "nodeType", nodeType,
                "configuration", Map.of("threshold", threshold)
        );
        return intelligenceClientService.cluster(request);
    }

    public JsonNode localize(FailureLocalizationRequest request) {
        validateEvidence(
                request.failingNodeId(),
                request.errorLog(),
                request.stackTrace(),
                request.failurePathNodeIds()
        );
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(
                request.repositoryId(),
                request.snapshotId()
        );
        return localize(loaded, failurePayload(
                request.failingNodeId(),
                request.errorLog(),
                request.stackTrace(),
                request.failurePathNodeIds()
        ), request.configuration());
    }

    public FailureRecordResponse recordFailure(String repositoryId, CreateFailureRequest request) {
        validateEvidence(
                request.failingNodeId(),
                request.errorLog(),
                request.stackTrace(),
                request.failurePathNodeIds()
        );
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(repositoryId, request.snapshotId());
        JsonNode localization = localize(loaded, failurePayload(
                request.failingNodeId(),
                request.errorLog(),
                request.stackTrace(),
                request.failurePathNodeIds()
        ), request.localizationConfiguration());
        return failureHistoryService.create(loaded, request, localization);
    }

    private JsonNode localize(
            GraphLoaderService.LoadedGraph loaded,
            Map<String, Object> failure,
            Map<String, Object> configuration
    ) {
        failure.put("repositoryId", loaded.repository().getId().toString());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("graph", loaded.graph().toPayload());
        request.put("failure", failure);
        request.put("history", failureHistoryService.historicalFailures(loaded));
        request.put("configuration", configuration != null ? configuration : Map.of());
        return intelligenceClientService.localize(request);
    }

    private Map<String, Object> failurePayload(
            String failingNodeId,
            String errorLog,
            String stackTrace,
            List<String> failurePathNodeIds
    ) {
        Map<String, Object> failure = new LinkedHashMap<>();
        failure.put("failingNodeId", failingNodeId);
        failure.put("errorLog", errorLog);
        failure.put("stackTrace", stackTrace);
        failure.put("failurePathNodeIds", failurePathNodeIds != null ? failurePathNodeIds : List.of());
        return failure;
    }

    private void validateEvidence(
            String failingNodeId,
            String errorLog,
            String stackTrace,
            List<String> failurePathNodeIds
    ) {
        if (isBlank(failingNodeId)
                && isBlank(errorLog)
                && isBlank(stackTrace)
                && (failurePathNodeIds == null || failurePathNodeIds.isEmpty())) {
            throw new ValidationException(
                    "FAILURE_EVIDENCE_REQUIRED",
                    "At least one failure evidence field is required"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
