package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.AnalysisResultEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResultEntity, UUID> {
    Optional<AnalysisResultEntity> findTopBySnapshotOrderByCreatedAtDesc(RepositorySnapshotEntity snapshot);
}
