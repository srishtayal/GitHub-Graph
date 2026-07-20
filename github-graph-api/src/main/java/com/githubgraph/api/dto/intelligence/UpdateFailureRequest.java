package com.githubgraph.api.dto.intelligence;

import java.time.Instant;
import java.util.List;

public record UpdateFailureRequest(
        String status,
        List<String> confirmedRootCauseNodeIds,
        String resolutionNotes,
        Instant resolvedAt
) {
}
