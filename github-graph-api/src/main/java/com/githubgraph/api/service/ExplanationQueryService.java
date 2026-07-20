package com.githubgraph.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.explanation.GroundedExplanationRequest;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ExplanationQueryService {

    private final GraphLoaderService graphLoaderService;
    private final FailureHistoryService failureHistoryService;
    private final IntelligenceClientService intelligenceClientService;

    public ExplanationQueryService(
            GraphLoaderService graphLoaderService,
            FailureHistoryService failureHistoryService,
            IntelligenceClientService intelligenceClientService
    ) {
        this.graphLoaderService = graphLoaderService;
        this.failureHistoryService = failureHistoryService;
        this.intelligenceClientService = intelligenceClientService;
    }

    public JsonNode query(GroundedExplanationRequest query) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadLatestGraph(query.repositoryId());
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("repositoryId", loaded.repository().getId().toString());
        request.put("query", query.query());
        putIfPresent(request, "targetNodeId", query.targetNodeId());
        putIfPresent(request, "stackTrace", query.stackTrace());
        putIfPresent(request, "errorLog", query.errorLog());
        request.put("graph", loaded.graph().toPayload());
        request.put("history", failureHistoryService.historicalFailures(loaded));
        request.put("repositoryMetadata", repositoryMetadata(loaded.repository(), loaded.snapshot()));
        request.put("snapshotMetadata", snapshotMetadata(loaded.repository(), loaded.snapshot()));
        return intelligenceClientService.queryExplanation(request);
    }

    private Map<String, Object> repositoryMetadata(
            RepositoryEntity repository,
            RepositorySnapshotEntity snapshot
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("repositoryId", repository.getId().toString());
        metadata.put("githubUrl", repository.getGithubUrl());
        metadata.put("owner", repository.getOwner());
        metadata.put("name", repository.getName());
        metadata.put("defaultBranch", repository.getDefaultBranch());
        metadata.put("public", repository.isPublic());
        metadata.put("totalFiles", snapshot.getTotalFiles());
        metadata.put("totalDirectories", snapshot.getTotalDirectories());
        metadata.put("languageSummary", snapshot.getLanguageSummaryJson());
        return metadata;
    }

    private Map<String, Object> snapshotMetadata(
            RepositoryEntity repository,
            RepositorySnapshotEntity snapshot
    ) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("repositoryId", repository.getId().toString());
        metadata.put("snapshotId", snapshot.getId().toString());
        metadata.put("branchName", snapshot.getBranchName());
        metadata.put("commitSha", snapshot.getCommitSha());
        return metadata;
    }

    private void putIfPresent(Map<String, Object> request, String key, String value) {
        if (value != null && !value.isBlank()) {
            request.put(key, value);
        }
    }
}
