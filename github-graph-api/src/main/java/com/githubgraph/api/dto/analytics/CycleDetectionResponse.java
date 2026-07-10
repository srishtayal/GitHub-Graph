package com.githubgraph.api.dto.analytics;

import com.githubgraph.api.analytics.CycleDetector;
import java.util.List;

public record CycleDetectionResponse(
        String repositoryId,
        boolean hasCycles,
        int totalCycles,
        List<CyclePath> cycles
) {
    public static CycleDetectionResponse from(String repositoryId, CycleDetector.CycleResult result) {
        List<CyclePath> cycles = result.cycles().stream()
                .map(CyclePath::new)
                .toList();
        return new CycleDetectionResponse(repositoryId, result.hasCycles(), cycles.size(), cycles);
    }

    public record CyclePath(
            List<String> nodeIds
    ) {
    }
}
