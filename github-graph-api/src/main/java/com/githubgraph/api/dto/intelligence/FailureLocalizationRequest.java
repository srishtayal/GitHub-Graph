package com.githubgraph.api.dto.intelligence;

import java.util.List;
import java.util.Map;

public record FailureLocalizationRequest(
        String repositoryId,
        String snapshotId,
        String failingNodeId,
        String errorLog,
        String stackTrace,
        List<String> failurePathNodeIds,
        Map<String, Object> configuration
) {
}
