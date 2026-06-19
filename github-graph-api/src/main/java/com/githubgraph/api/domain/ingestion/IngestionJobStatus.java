package com.githubgraph.api.domain.ingestion;

public enum IngestionJobStatus {
    PENDING,
    VALIDATING,
    CLONING,
    ANALYZING,
    STORING,
    COMPLETED,
    FAILED
}
