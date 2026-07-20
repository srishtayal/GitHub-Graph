package com.githubgraph.api.exception;

import com.githubgraph.api.domain.ingestion.IngestionFailureCategory;

public class IngestionException extends RuntimeException {

    private final IngestionFailureCategory category;

    public IngestionException(IngestionFailureCategory category, String message) {
        super(message);
        this.category = category;
    }

    public IngestionException(IngestionFailureCategory category, String message, Throwable cause) {
        super(message, cause);
        this.category = category;
    }

    public IngestionFailureCategory getCategory() {
        return category;
    }
}
