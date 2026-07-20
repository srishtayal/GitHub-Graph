package com.githubgraph.api.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "failure_root_cause_nodes")
public class FailureRootCauseNodeEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "failure_id", nullable = false)
    private FailureRecordEntity failure;

    @Column(name = "node_id", nullable = false)
    private String nodeId;

    @Column(name = "confirmed_at", nullable = false)
    private Instant confirmedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (confirmedAt == null) {
            confirmedAt = Instant.now();
        }
    }

    public void setFailure(FailureRecordEntity failure) {
        this.failure = failure;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }
}
