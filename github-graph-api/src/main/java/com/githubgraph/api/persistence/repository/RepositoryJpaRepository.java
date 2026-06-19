package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.RepositoryEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepositoryJpaRepository extends JpaRepository<RepositoryEntity, UUID> {

    Optional<RepositoryEntity> findByGithubUrl(String githubUrl);
}
