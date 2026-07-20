package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.dto.RepositoryCatalogResponse;
import com.githubgraph.api.dto.RepositorySummaryResponse;
import com.githubgraph.api.dto.SnapshotHistoryResponse;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.githubgraph.api.persistence.entity.SavedRepositoryEntity;
import com.githubgraph.api.persistence.entity.UserEntity;
import com.githubgraph.api.persistence.repository.RepositoryJpaRepository;
import com.githubgraph.api.persistence.repository.RepositorySnapshotJpaRepository;
import com.githubgraph.api.persistence.repository.SavedRepositoryJpaRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepositoryCatalogService {

    private final AuthService authService;
    private final SavedRepositoryJpaRepository savedRepositoryJpaRepository;
    private final RepositoryJpaRepository repositoryJpaRepository;
    private final RepositorySnapshotJpaRepository snapshotJpaRepository;
    private final ObjectMapper objectMapper;

    public RepositoryCatalogService(
            AuthService authService,
            SavedRepositoryJpaRepository savedRepositoryJpaRepository,
            RepositoryJpaRepository repositoryJpaRepository,
            RepositorySnapshotJpaRepository snapshotJpaRepository,
            ObjectMapper objectMapper
    ) {
        this.authService = authService;
        this.savedRepositoryJpaRepository = savedRepositoryJpaRepository;
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.snapshotJpaRepository = snapshotJpaRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void saveForCurrentUser(RepositoryEntity repository) {
        UserEntity user = authService.currentUser();
        if (savedRepositoryJpaRepository.findByUserAndRepository(user, repository).isPresent()) {
            return;
        }
        SavedRepositoryEntity saved = new SavedRepositoryEntity();
        saved.setUser(user);
        saved.setRepository(repository);
        savedRepositoryJpaRepository.save(saved);
    }

    @Transactional(readOnly = true)
    public RepositoryCatalogResponse listSaved() {
        UserEntity user = authService.currentUser();
        List<RepositorySummaryResponse> items = savedRepositoryJpaRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(SavedRepositoryEntity::getRepository)
                .map(this::summary)
                .toList();
        return new RepositoryCatalogResponse(items);
    }

    @Transactional(readOnly = true)
    public SnapshotHistoryResponse history(String repositoryId) {
        RepositoryEntity repository = findRepository(repositoryId);
        assertAccess(repository);
        return new SnapshotHistoryResponse(snapshotJpaRepository.findByRepositoryOrderByCreatedAtDesc(repository).stream()
                .map(this::snapshotItem)
                .toList());
    }

    public void assertAccess(RepositoryEntity repository) {
        if (!authService.authenticationEnabled()) {
            return;
        }
        UserEntity user = authService.currentUser();
        if (savedRepositoryJpaRepository.findByUserAndRepository(user, repository).isEmpty()) {
            throw new NotFoundException("Repository not found");
        }
    }

    public RepositoryEntity findRepository(String repositoryId) {
        return repositoryJpaRepository.findById(UUID.fromString(repositoryId))
                .orElseThrow(() -> new NotFoundException("Repository not found"));
    }

    private RepositorySummaryResponse summary(RepositoryEntity repository) {
        return new RepositorySummaryResponse(
                repository.getId().toString(), repository.getGithubUrl(), repository.getOwner(), repository.getName(),
                repository.getStatus(), snapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)
                        .map(this::latestSnapshot).orElse(null)
        );
    }

    private RepositorySummaryResponse.LatestSnapshot latestSnapshot(RepositorySnapshotEntity snapshot) {
        return new RepositorySummaryResponse.LatestSnapshot(
                snapshot.getId().toString(), snapshot.getIngestionJob().getId().toString(), snapshot.getBranchName(), snapshot.getCommitSha(),
                snapshot.getTotalFiles(), snapshot.getTotalDirectories(), readLanguages(snapshot.getLanguageSummaryJson()), instant(snapshot.getCreatedAt())
        );
    }

    private SnapshotHistoryResponse.SnapshotItem snapshotItem(RepositorySnapshotEntity snapshot) {
        return new SnapshotHistoryResponse.SnapshotItem(
                snapshot.getId().toString(), snapshot.getIngestionJob().getId().toString(), snapshot.getBranchName(),
                snapshot.getCommitSha(), snapshot.getCommitMessage(), snapshot.getCommitAuthor(),
                instant(snapshot.getCommittedAt()), instant(snapshot.getCreatedAt()), snapshot.getTotalFiles(),
                snapshot.getTotalDirectories(), readLanguages(snapshot.getLanguageSummaryJson())
        );
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readLanguages(String value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("Unable to read language summary", exception); }
    }

    private String instant(java.time.Instant instant) { return instant != null ? instant.toString() : null; }
}
