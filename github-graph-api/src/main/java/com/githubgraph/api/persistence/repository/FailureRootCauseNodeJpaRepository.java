package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.FailureRecordEntity;
import com.githubgraph.api.persistence.entity.FailureRootCauseNodeEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureRootCauseNodeJpaRepository extends JpaRepository<FailureRootCauseNodeEntity, UUID> {

    List<FailureRootCauseNodeEntity> findByFailureOrderByNodeIdAsc(FailureRecordEntity failure);

    void deleteByFailure(FailureRecordEntity failure);
}
