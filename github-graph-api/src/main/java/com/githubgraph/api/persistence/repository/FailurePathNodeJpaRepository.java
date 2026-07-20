package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.FailurePathNodeEntity;
import com.githubgraph.api.persistence.entity.FailureRecordEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailurePathNodeJpaRepository extends JpaRepository<FailurePathNodeEntity, UUID> {

    List<FailurePathNodeEntity> findByFailureOrderByPositionAsc(FailureRecordEntity failure);
}
