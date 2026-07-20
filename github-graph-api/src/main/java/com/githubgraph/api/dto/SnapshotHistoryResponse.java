package com.githubgraph.api.dto;

import java.util.List;
import java.util.Map;

public record SnapshotHistoryResponse(List<SnapshotItem> snapshots) {
    public record SnapshotItem(
            String snapshotId,
            String ingestionJobId,
            String branchName,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            String committedAt,
            String analyzedAt,
            int totalFiles,
            int totalDirectories,
            Map<String, Integer> languageSummary
    ) {
    }
}
