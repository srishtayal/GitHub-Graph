package com.githubgraph.api.dto.intelligence;

import java.util.List;

public record FailureCollectionResponse(
        String repositoryId,
        String snapshotId,
        List<FailureRecordResponse> failures
) {
}
