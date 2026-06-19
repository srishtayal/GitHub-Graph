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
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "repository_snapshots")
public class RepositorySnapshotEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repository_id", nullable = false)
    private RepositoryEntity repository;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingestion_job_id", nullable = false)
    private IngestionJobEntity ingestionJob;

    @Column(name = "branch_name")
    private String branchName;

    @Column(name = "commit_sha")
    private String commitSha;

    @Column(name = "commit_message")
    private String commitMessage;

    @Column(name = "commit_author")
    private String commitAuthor;

    @Column(name = "committed_at")
    private Instant committedAt;

    @Column(name = "root_directory", nullable = false)
    private String rootDirectory;

    @Column(name = "total_files", nullable = false)
    private int totalFiles;

    @Column(name = "total_directories", nullable = false)
    private int totalDirectories;

    @Column(name = "language_summary_json", nullable = false, columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String languageSummaryJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public RepositoryEntity getRepository() {
        return repository;
    }

    public void setRepository(RepositoryEntity repository) {
        this.repository = repository;
    }

    public IngestionJobEntity getIngestionJob() {
        return ingestionJob;
    }

    public void setIngestionJob(IngestionJobEntity ingestionJob) {
        this.ingestionJob = ingestionJob;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public void setCommitSha(String commitSha) {
        this.commitSha = commitSha;
    }

    public String getCommitMessage() {
        return commitMessage;
    }

    public void setCommitMessage(String commitMessage) {
        this.commitMessage = commitMessage;
    }

    public String getCommitAuthor() {
        return commitAuthor;
    }

    public void setCommitAuthor(String commitAuthor) {
        this.commitAuthor = commitAuthor;
    }

    public Instant getCommittedAt() {
        return committedAt;
    }

    public void setCommittedAt(Instant committedAt) {
        this.committedAt = committedAt;
    }

    public String getRootDirectory() {
        return rootDirectory;
    }

    public void setRootDirectory(String rootDirectory) {
        this.rootDirectory = rootDirectory;
    }

    public int getTotalFiles() {
        return totalFiles;
    }

    public void setTotalFiles(int totalFiles) {
        this.totalFiles = totalFiles;
    }

    public int getTotalDirectories() {
        return totalDirectories;
    }

    public void setTotalDirectories(int totalDirectories) {
        this.totalDirectories = totalDirectories;
    }

    public String getLanguageSummaryJson() {
        return languageSummaryJson;
    }

    public void setLanguageSummaryJson(String languageSummaryJson) {
        this.languageSummaryJson = languageSummaryJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
