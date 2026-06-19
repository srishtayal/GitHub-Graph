package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.FileEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileJpaRepository extends JpaRepository<FileEntity, UUID> {

    List<FileEntity> findBySnapshotOrderByRelativePathAsc(RepositorySnapshotEntity snapshot);
}
