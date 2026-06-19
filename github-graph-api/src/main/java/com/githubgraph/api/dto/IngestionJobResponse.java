package com.githubgraph.api.dto;

public record IngestionJobResponse(
        String jobId,
        String repositoryId,
        String status,
        String errorMessage,
        String createdAt,
        String startedAt,
        String finishedAt
) {
}
