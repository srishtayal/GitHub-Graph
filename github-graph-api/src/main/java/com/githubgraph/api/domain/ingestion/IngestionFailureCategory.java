package com.githubgraph.api.domain.ingestion;

public enum IngestionFailureCategory {
    VALIDATION_FAILED,
    CLONE_TIMEOUT,
    CLONE_SIZE_LIMIT,
    CLONE_FAILED,
    ANALYSIS_FAILED,
    STORAGE_FAILED,
    INTERNAL_ERROR
}
