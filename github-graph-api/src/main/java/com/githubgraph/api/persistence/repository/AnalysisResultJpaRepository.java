package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.AnalysisResultEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalysisResultJpaRepository extends JpaRepository<AnalysisResultEntity, UUID> {
}
