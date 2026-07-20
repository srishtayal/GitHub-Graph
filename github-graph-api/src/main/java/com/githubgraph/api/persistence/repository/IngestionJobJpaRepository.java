package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.IngestionJobEntity;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobJpaRepository extends JpaRepository<IngestionJobEntity, UUID> {
    Optional<IngestionJobEntity> findTopByRepositoryOrderByCreatedAtDesc(RepositoryEntity repository);
}
