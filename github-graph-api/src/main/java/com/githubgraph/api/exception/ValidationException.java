package com.githubgraph.api.exception;

public class ValidationException extends RuntimeException {

    private final String code;

    public ValidationException(String message) {
        this("VALIDATION_FAILED", message);
    }

    public ValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
