package com.githubgraph.api.dto;

public record AnalysisServiceRequest(
        String ingestionJobId,
        String repositoryId,
        String localPath,
        String githubUrl
) {
}
