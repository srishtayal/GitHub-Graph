package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.DirectoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface DirectoryJpaRepository extends JpaRepository<DirectoryEntity, UUID> {
}
