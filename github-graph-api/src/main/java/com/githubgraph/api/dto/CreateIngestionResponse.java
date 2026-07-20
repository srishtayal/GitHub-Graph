package com.githubgraph.api.dto;

public record CreateIngestionResponse(
        String jobId,
        String repositoryId,
        String status,
        boolean reused
) {
}
