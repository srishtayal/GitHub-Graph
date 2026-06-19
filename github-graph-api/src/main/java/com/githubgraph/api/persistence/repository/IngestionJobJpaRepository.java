package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.IngestionJobEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngestionJobJpaRepository extends JpaRepository<IngestionJobEntity, UUID> {
}
