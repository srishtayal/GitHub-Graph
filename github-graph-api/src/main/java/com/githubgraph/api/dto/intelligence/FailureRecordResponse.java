package com.githubgraph.api.dto.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record FailureRecordResponse(
        String failureId,
        String repositoryId,
        String snapshotId,
        String status,
        String failingNodeId,
        String errorLog,
        String stackTrace,
        ErrorSignatureResponse errorSignature,
        List<String> resolvedFailurePathNodeIds,
        List<String> confirmedRootCauseNodeIds,
        String resolutionNotes,
        Instant occurredAt,
        Instant resolvedAt,
        Instant createdAt,
        Instant updatedAt,
        JsonNode localization
) {
    public record ErrorSignatureResponse(
            String exceptionType,
            String messageFingerprint
    ) {
    }
}
