package com.githubgraph.api.service;

import com.githubgraph.api.analytics.model.GraphView;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.githubgraph.api.persistence.repository.RepositoryJpaRepository;
import com.githubgraph.api.persistence.repository.RepositorySnapshotJpaRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GraphLoaderService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final RepositorySnapshotJpaRepository repositorySnapshotJpaRepository;
    private final RepositoryGraphService repositoryGraphService;

    public GraphLoaderService(
            RepositoryJpaRepository repositoryJpaRepository,
            RepositorySnapshotJpaRepository repositorySnapshotJpaRepository,
            RepositoryGraphService repositoryGraphService
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.repositorySnapshotJpaRepository = repositorySnapshotJpaRepository;
        this.repositoryGraphService = repositoryGraphService;
    }

    public LoadedGraph loadLatestGraph(String repositoryId) {
        return loadGraph(repositoryId, null);
    }

    public LoadedGraph loadGraph(String repositoryId, String snapshotId) {
        UUID parsedRepositoryId = UUID.fromString(repositoryId);
        RepositoryEntity repository = repositoryJpaRepository.findById(parsedRepositoryId)
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        RepositorySnapshotEntity snapshot = snapshotId == null || snapshotId.isBlank()
                ? repositorySnapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)
                        .orElseThrow(() -> new NotFoundException("Repository snapshot not found"))
                : loadOwnedSnapshot(repository, snapshotId);
        GraphView graph = repositoryGraphService.loadGraphView(parsedRepositoryId, snapshot);
        return new LoadedGraph(repository, snapshot, graph);
    }

    private RepositorySnapshotEntity loadOwnedSnapshot(RepositoryEntity repository, String snapshotId) {
        return repositorySnapshotJpaRepository.findByIdAndRepository(UUID.fromString(snapshotId), repository)
                .orElseThrow(() -> new NotFoundException("Repository snapshot not found"));
    }

    public record LoadedGraph(
            RepositoryEntity repository,
            RepositorySnapshotEntity snapshot,
            GraphView graph
    ) {
    }
}
