package com.githubgraph.api.dto.explanation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GroundedExplanationRequest(
        @NotBlank String repositoryId,
        @NotBlank @Size(max = 4000) String query,
        String targetNodeId,
        @Size(max = 20000) String stackTrace,
        @Size(max = 20000) String errorLog
) {
}
