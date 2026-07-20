package com.githubgraph.api.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failure_evidence")
public class FailureEvidenceEntity {

    @Id
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "failure_id", nullable = false, unique = true)
    private FailureRecordEntity failure;

    @Column(name = "stack_trace")
    private String stackTrace;

    @Column(name = "exception_type")
    private String exceptionType;

    @Column(name = "message_fingerprint")
    private String messageFingerprint;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public void setFailure(FailureRecordEntity failure) {
        this.failure = failure;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    public String getExceptionType() {
        return exceptionType;
    }

    public void setExceptionType(String exceptionType) {
        this.exceptionType = exceptionType;
    }

    public String getMessageFingerprint() {
        return messageFingerprint;
    }

    public void setMessageFingerprint(String messageFingerprint) {
        this.messageFingerprint = messageFingerprint;
    }
}
