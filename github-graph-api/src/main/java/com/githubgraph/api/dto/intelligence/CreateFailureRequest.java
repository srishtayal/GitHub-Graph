package com.githubgraph.api.dto.intelligence;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record CreateFailureRequest(
        String snapshotId,
        String failingNodeId,
        String errorLog,
        String stackTrace,
        List<String> failurePathNodeIds,
        Instant occurredAt,
        Map<String, Object> localizationConfiguration
) {
}
