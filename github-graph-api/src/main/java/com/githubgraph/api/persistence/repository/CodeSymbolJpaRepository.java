package com.githubgraph.api.persistence.repository;

import com.githubgraph.api.persistence.entity.CodeSymbolEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeSymbolJpaRepository extends JpaRepository<CodeSymbolEntity, UUID> {

    List<CodeSymbolEntity> findBySnapshotOrderByNameAsc(RepositorySnapshotEntity snapshot);
}
