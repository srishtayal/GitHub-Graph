package com.githubgraph.api.dto;

import java.util.Map;

public record RepositorySummaryResponse(
        String repositoryId,
        String githubUrl,
        String owner,
        String name,
        String status,
        LatestSnapshot latestSnapshot
) {
    public record LatestSnapshot(
            String snapshotId,
            String ingestionJobId,
            String branchName,
            String commitSha,
            int totalFiles,
            int totalDirectories,
            Map<String, Integer> languageSummary,
            String analyzedAt
    ) {
    }
}
