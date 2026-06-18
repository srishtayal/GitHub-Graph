package com.githubgraph.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateIngestionRequest(
        @NotBlank(message = "githubUrl is required")
        String githubUrl
) {
}
