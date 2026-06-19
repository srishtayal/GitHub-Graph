package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.ImportRelationEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportRelationJpaRepository extends JpaRepository<ImportRelationEntity, UUID> {

    List<ImportRelationEntity> findBySnapshotOrderByImportValueAsc(RepositorySnapshotEntity snapshot);
}
