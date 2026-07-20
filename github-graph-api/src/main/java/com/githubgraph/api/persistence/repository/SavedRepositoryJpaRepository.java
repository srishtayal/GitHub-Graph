package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.SavedRepositoryEntity;
import com.githubgraph.api.persistence.entity.UserEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavedRepositoryJpaRepository extends JpaRepository<SavedRepositoryEntity, UUID> {
    Optional<SavedRepositoryEntity> findByUserAndRepository(UserEntity user, RepositoryEntity repository);
    List<SavedRepositoryEntity> findByUserOrderByCreatedAtDesc(UserEntity user);
}
