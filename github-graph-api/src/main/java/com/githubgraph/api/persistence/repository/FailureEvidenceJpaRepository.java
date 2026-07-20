package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.FailureEvidenceEntity;
import com.githubgraph.api.persistence.entity.FailureRecordEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureEvidenceJpaRepository extends JpaRepository<FailureEvidenceEntity, UUID> {

    Optional<FailureEvidenceEntity> findByFailure(FailureRecordEntity failure);
}
