package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositorySnapshotJpaRepository extends JpaRepository<RepositorySnapshotEntity, UUID> {

    Optional<RepositorySnapshotEntity> findTopByRepositoryOrderByCreatedAtDesc(RepositoryEntity repository);
}
