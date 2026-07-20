package com.githubgraph.api.dto;

public record IngestionJobResponse(
        String jobId,
        String repositoryId,
        String status,
        String errorMessage,
        String errorCategory,
        String createdAt,
        String startedAt,
        String finishedAt
) {
}
