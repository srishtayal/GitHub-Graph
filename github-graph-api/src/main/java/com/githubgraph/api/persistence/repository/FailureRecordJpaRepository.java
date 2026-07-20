package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.FailureRecordEntity;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureRecordJpaRepository extends JpaRepository<FailureRecordEntity, UUID> {

    List<FailureRecordEntity> findByRepositoryAndSnapshotOrderByOccurredAtDesc(
            RepositoryEntity repository,
            RepositorySnapshotEntity snapshot
    );
}
