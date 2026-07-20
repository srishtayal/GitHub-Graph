package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.dto.AnalysisServiceResponse;
import com.githubgraph.api.dto.CreateIngestionRequest;
import com.githubgraph.api.dto.CreateIngestionResponse;
import com.githubgraph.api.exception.ValidationException;
import com.githubgraph.api.job.IngestionJobExecutor;
import com.githubgraph.api.persistence.entity.AnalysisResultEntity;
import com.githubgraph.api.persistence.entity.IngestionJobEntity;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.githubgraph.api.persistence.repository.AnalysisResultJpaRepository;
import com.githubgraph.api.persistence.repository.CodeSymbolJpaRepository;
import com.githubgraph.api.persistence.repository.DirectoryJpaRepository;
import com.githubgraph.api.persistence.repository.FileJpaRepository;
import com.githubgraph.api.persistence.repository.ImportRelationJpaRepository;
import com.githubgraph.api.persistence.repository.IngestionJobJpaRepository;
import com.githubgraph.api.persistence.repository.RepositoryJpaRepository;
import com.githubgraph.api.persistence.repository.RepositorySnapshotJpaRepository;
import com.githubgraph.api.util.GithubRepoRef;
import com.githubgraph.api.util.GithubUrlValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class IngestionOrchestratorServiceTest {

    @Mock
    private RepositoryJpaRepository repositoryJpaRepository;

    @Mock
    private IngestionJobJpaRepository ingestionJobJpaRepository;

    @Mock
    private RepositorySnapshotJpaRepository repositorySnapshotJpaRepository;

    @Mock
    private DirectoryJpaRepository directoryJpaRepository;

    @Mock
    private FileJpaRepository fileJpaRepository;

    @Mock
    private CodeSymbolJpaRepository codeSymbolJpaRepository;

    @Mock
    private ImportRelationJpaRepository importRelationJpaRepository;

    @Mock
    private AnalysisResultJpaRepository analysisResultJpaRepository;

    @Mock
    private GithubUrlValidator githubUrlValidator;

    @Mock
    private GithubRepositoryValidationService githubRepositoryValidationService;

    @Mock
    private RepositoryCloneService repositoryCloneService;

    @Mock
    private AnalysisClientService analysisClientService;

    @Mock
    private RepositoryGraphService repositoryGraphService;

    @Mock
    private AnalyticsClientService analyticsClientService;

    @Mock
    private RepositoryCatalogService repositoryCatalogService;

    @Mock
    private IngestionJobExecutor ingestionJobExecutor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IngestionOrchestratorService service;

    @Test
    void createsJobOnlyAfterPublicRepositoryValidationSucceeds() {
        GithubRepoRef repoRef = new GithubRepoRef(
                "https://github.com/pallets/itsdangerous",
                "pallets",
                "itsdangerous"
        );
        when(githubUrlValidator.validateAndNormalize(repoRef.normalizedUrl())).thenReturn(repoRef);
        when(repositoryJpaRepository.findByGithubUrl(repoRef.normalizedUrl())).thenReturn(Optional.empty());
        when(ingestionJobJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(any(RepositoryEntity.class)))
                .thenReturn(Optional.empty());
        when(repositoryJpaRepository.save(any(RepositoryEntity.class))).thenAnswer(invocation -> {
            RepositoryEntity repository = invocation.getArgument(0);
            repository.prePersist();
            return repository;
        });
        when(ingestionJobJpaRepository.saveAndFlush(any(IngestionJobEntity.class))).thenAnswer(invocation -> {
            IngestionJobEntity job = invocation.getArgument(0);
            job.prePersist();
            return job;
        });

        TransactionSynchronizationManager.initSynchronization();
        try {
            CreateIngestionResponse response = service.createIngestion(
                    new CreateIngestionRequest(repoRef.normalizedUrl())
            );

            assertEquals("PENDING", response.status());
            assertNotNull(response.jobId());
            verify(githubRepositoryValidationService).verifyPublic(repoRef);
            verify(ingestionJobExecutor, never()).processAsync(any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);
            verify(ingestionJobExecutor).processAsync(UUID.fromString(response.jobId()));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void repositoryValidationFailureCreatesNoDatabaseRecords() {
        GithubRepoRef repoRef = new GithubRepoRef(
                "https://github.com/example/private",
                "example",
                "private"
        );
        when(githubUrlValidator.validateAndNormalize(repoRef.normalizedUrl())).thenReturn(repoRef);
        doThrow(new ValidationException("PRIVATE_REPOSITORY", "Private repositories are not supported"))
                .when(githubRepositoryValidationService)
                .verifyPublic(repoRef);

        assertThrows(
                ValidationException.class,
                () -> service.createIngestion(new CreateIngestionRequest(repoRef.normalizedUrl()))
        );

        verify(repositoryJpaRepository, never()).findByGithubUrl(any());
        verify(repositoryJpaRepository, never()).save(any());
        verify(ingestionJobJpaRepository, never()).saveAndFlush(any());
        verify(ingestionJobExecutor, never()).processAsync(any());
    }

    @Test
    void getRepositoryGraphReturnsPersistedGraphFromNeo4jService() throws Exception {
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RepositoryEntity repository = new RepositoryEntity();
        repository.setId(repositoryId);

        RepositorySnapshotEntity snapshot = new RepositorySnapshotEntity();
        snapshot.setRepository(repository);
        JsonNode graphPayload = new ObjectMapper().readTree("""
                {"nodes":[{"id":"repo:1","type":"repo","label":"project","properties":{}}],"edges":[{"id":"edge:1","source":"repo:1","target":"file:1","type":"BELONGS_TO","properties":{}}]}
                """);

        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.of(repository));
        when(repositorySnapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)).thenReturn(Optional.of(snapshot));
        when(repositoryGraphService.loadRepositoryGraph(repositoryId, snapshot)).thenReturn(graphPayload);

        JsonNode graph = service.getRepositoryGraph(repositoryId.toString());

        assertNotNull(graph);
        assertEquals(1, graph.path("nodes").size());
        assertEquals(1, graph.path("edges").size());
        assertEquals("repo:1", graph.path("nodes").get(0).path("id").asText());
    }

    @Test
    void persistAnalysisStoresGraphInNeo4j() throws Exception {
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RepositoryEntity repository = new RepositoryEntity();
        repository.setId(repositoryId);

        IngestionJobEntity job = new IngestionJobEntity();
        job.setRepository(repository);

        RepositoryCloneService.CloneResult cloneResult = new RepositoryCloneService.CloneResult(
                "/workspace/repos/repository/job",
                "main",
                "abc123",
                "Initial commit",
                "Jane Doe",
                java.time.Instant.parse("2026-07-10T10:15:30Z")
        );

        AnalysisServiceResponse analysis = new AnalysisServiceResponse(
                "job-123",
                "COMPLETED",
                new AnalysisServiceResponse.Snapshot("main", "abc123"),
                new AnalysisServiceResponse.Summary(1, 0, java.util.Map.of("Python", 1), 0, 0, 0, 0, 0, 1, 0),
                java.util.List.of(),
                java.util.List.of(
                        new AnalysisServiceResponse.FileItem("app.py", "app.py", ".py", "Python", 42, false)
                ),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                new AnalysisServiceResponse.Graph(
                        java.util.List.of(java.util.Map.of("id", "repo:1", "type", "repo", "label", "project", "properties", java.util.Map.of())),
                        java.util.List.of()
                )
        );

        when(repositoryJpaRepository.save(any(RepositoryEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(repositorySnapshotJpaRepository.save(any(RepositorySnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileJpaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(analysisResultJpaRepository.save(any(AnalysisResultEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        service.persistAnalysis(job, cloneResult, analysis);

        verify(repositoryGraphService, times(1)).replaceSnapshotGraph(eq(repositoryId), any(RepositorySnapshotEntity.class), eq(analysis.graph()));
    }
}
