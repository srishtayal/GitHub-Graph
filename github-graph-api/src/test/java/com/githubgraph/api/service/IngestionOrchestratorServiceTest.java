package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.job.IngestionJobExecutor;
import com.githubgraph.api.persistence.entity.AnalysisResultEntity;
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
import com.githubgraph.api.util.GithubUrlValidator;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private RepositoryCloneService repositoryCloneService;

    @Mock
    private AnalysisClientService analysisClientService;

    @Mock
    private AnalyticsClientService analyticsClientService;

    @Mock
    private IngestionJobExecutor ingestionJobExecutor;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IngestionOrchestratorService service;

    @Test
    void getRepositoryGraphReturnsGraphFromLatestAnalysisPayload() throws Exception {
        UUID repositoryId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        RepositoryEntity repository = new RepositoryEntity();
        repository.setId(repositoryId);

        RepositorySnapshotEntity snapshot = new RepositorySnapshotEntity();
        snapshot.setRepository(repository);

        AnalysisResultEntity result = new AnalysisResultEntity();
        String payloadJson = """
                {"graph":{"nodes":[{"id":"repo:1","type":"repo","label":"project"}],"edges":[{"id":"edge:1","source":"repo:1","target":"file:1","type":"BELONGS_TO"}]},"summary":{"totalGraphNodes":1,"totalGraphEdges":1}}
            """;
        result.setPayloadJson(payloadJson);

        JsonNode payload = new ObjectMapper().readTree(payloadJson);

        when(repositoryJpaRepository.findById(repositoryId)).thenReturn(Optional.of(repository));
        when(repositorySnapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)).thenReturn(Optional.of(snapshot));
        when(analysisResultJpaRepository.findTopBySnapshotOrderByCreatedAtDesc(snapshot)).thenReturn(Optional.of(result));
        when(objectMapper.readTree(payloadJson)).thenReturn(payload);

        JsonNode graph = service.getRepositoryGraph(repositoryId.toString());

        assertNotNull(graph);
        assertEquals(1, graph.path("nodes").size());
        assertEquals(1, graph.path("edges").size());
        assertEquals("repo:1", graph.path("nodes").get(0).path("id").asText());
    }
}