package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.domain.ingestion.IngestionJobStatus;
import com.githubgraph.api.dto.AnalysisServiceRequest;
import com.githubgraph.api.dto.AnalysisServiceResponse;
import com.githubgraph.api.dto.CreateIngestionRequest;
import com.githubgraph.api.dto.CreateIngestionResponse;
import com.githubgraph.api.dto.FileSummaryResponse;
import com.githubgraph.api.dto.ImportSummaryResponse;
import com.githubgraph.api.dto.IngestionJobResponse;
import com.githubgraph.api.dto.RepositorySummaryResponse;
import com.githubgraph.api.dto.SymbolSummaryResponse;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.job.IngestionJobExecutor;
import com.githubgraph.api.persistence.entity.AnalysisResultEntity;
import com.githubgraph.api.persistence.entity.CodeSymbolEntity;
import com.githubgraph.api.persistence.entity.DirectoryEntity;
import com.githubgraph.api.persistence.entity.FileEntity;
import com.githubgraph.api.persistence.entity.ImportRelationEntity;
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
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IngestionOrchestratorService {

    private final RepositoryJpaRepository repositoryJpaRepository;
    private final IngestionJobJpaRepository ingestionJobJpaRepository;
    private final RepositorySnapshotJpaRepository repositorySnapshotJpaRepository;
    private final DirectoryJpaRepository directoryJpaRepository;
    private final FileJpaRepository fileJpaRepository;
    private final CodeSymbolJpaRepository codeSymbolJpaRepository;
    private final ImportRelationJpaRepository importRelationJpaRepository;
    private final AnalysisResultJpaRepository analysisResultJpaRepository;
    private final GithubUrlValidator githubUrlValidator;
    private final RepositoryCloneService repositoryCloneService;
    private final AnalysisClientService analysisClientService;
    private final ObjectMapper objectMapper;
    private final IngestionJobExecutor ingestionJobExecutor;

    public IngestionOrchestratorService(
            RepositoryJpaRepository repositoryJpaRepository,
            IngestionJobJpaRepository ingestionJobJpaRepository,
            RepositorySnapshotJpaRepository repositorySnapshotJpaRepository,
            DirectoryJpaRepository directoryJpaRepository,
            FileJpaRepository fileJpaRepository,
            CodeSymbolJpaRepository codeSymbolJpaRepository,
            ImportRelationJpaRepository importRelationJpaRepository,
            AnalysisResultJpaRepository analysisResultJpaRepository,
            GithubUrlValidator githubUrlValidator,
            RepositoryCloneService repositoryCloneService,
            AnalysisClientService analysisClientService,
            ObjectMapper objectMapper,
            IngestionJobExecutor ingestionJobExecutor
    ) {
        this.repositoryJpaRepository = repositoryJpaRepository;
        this.ingestionJobJpaRepository = ingestionJobJpaRepository;
        this.repositorySnapshotJpaRepository = repositorySnapshotJpaRepository;
        this.directoryJpaRepository = directoryJpaRepository;
        this.fileJpaRepository = fileJpaRepository;
        this.codeSymbolJpaRepository = codeSymbolJpaRepository;
        this.importRelationJpaRepository = importRelationJpaRepository;
        this.analysisResultJpaRepository = analysisResultJpaRepository;
        this.githubUrlValidator = githubUrlValidator;
        this.repositoryCloneService = repositoryCloneService;
        this.analysisClientService = analysisClientService;
        this.objectMapper = objectMapper;
        this.ingestionJobExecutor = ingestionJobExecutor;
    }

    @Transactional
    public CreateIngestionResponse createIngestion(CreateIngestionRequest request) {
        GithubRepoRef repoRef = githubUrlValidator.validateAndNormalize(request.githubUrl());

        RepositoryEntity repository = repositoryJpaRepository.findByGithubUrl(repoRef.normalizedUrl())
                .orElseGet(() -> createRepository(repoRef));

        IngestionJobEntity job = new IngestionJobEntity();
        job.setRepository(repository);
        job.setSubmittedUrl(repoRef.normalizedUrl());
        job.setStatus(IngestionJobStatus.PENDING.name());
        ingestionJobJpaRepository.saveAndFlush(job);

        ingestionJobExecutor.processAsync(job.getId());
        return new CreateIngestionResponse(job.getId().toString(), repository.getId().toString(), job.getStatus());
    }

    public IngestionJobResponse getJob(String jobId) {
        IngestionJobEntity job = ingestionJobJpaRepository.findById(UUID.fromString(jobId))
                .orElseThrow(() -> new NotFoundException("Ingestion job not found"));
        return mapJob(job);
    }

    public RepositorySummaryResponse getRepositorySummary(String repositoryId) {
        RepositoryEntity repository = repositoryJpaRepository.findById(UUID.fromString(repositoryId))
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        Optional<RepositorySnapshotEntity> latestSnapshot = repositorySnapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository);
        return new RepositorySummaryResponse(
                repository.getId().toString(),
                repository.getGithubUrl(),
                repository.getOwner(),
                repository.getName(),
                repository.getStatus(),
                latestSnapshot.map(this::mapSnapshot).orElse(null)
        );
    }

    public FileSummaryResponse getRepositoryFiles(String repositoryId) {
        RepositorySnapshotEntity snapshot = latestSnapshotFor(repositoryId);
        List<FileSummaryResponse.FileItem> items = fileJpaRepository.findBySnapshotOrderByRelativePathAsc(snapshot).stream()
                .map(file -> new FileSummaryResponse.FileItem(
                        file.getId().toString(),
                        file.getRelativePath(),
                        file.getLanguage(),
                        file.getSizeBytes()
                ))
                .toList();
        return new FileSummaryResponse(items);
    }

    public SymbolSummaryResponse getRepositorySymbols(String repositoryId) {
        RepositorySnapshotEntity snapshot = latestSnapshotFor(repositoryId);
        List<SymbolSummaryResponse.SymbolItem> items = codeSymbolJpaRepository.findBySnapshotOrderByNameAsc(snapshot).stream()
                .map(symbol -> new SymbolSummaryResponse.SymbolItem(
                        symbol.getId().toString(),
                        symbol.getFile() != null ? symbol.getFile().getId().toString() : null,
                        symbol.getSymbolType(),
                        symbol.getName(),
                        symbol.getQualifiedName(),
                        symbol.getLanguage(),
                        symbol.getStartLine(),
                        symbol.getEndLine()
                ))
                .toList();
        return new SymbolSummaryResponse(items);
    }

    public ImportSummaryResponse getRepositoryImports(String repositoryId) {
        RepositorySnapshotEntity snapshot = latestSnapshotFor(repositoryId);
        List<ImportSummaryResponse.ImportItem> items = importRelationJpaRepository.findBySnapshotOrderByImportValueAsc(snapshot).stream()
                .map(item -> new ImportSummaryResponse.ImportItem(
                        item.getFile() != null ? item.getFile().getId().toString() : null,
                        item.getImportValue(),
                        item.getResolvedPath()
                ))
                .toList();
        return new ImportSummaryResponse(items);
    }

    @Transactional
    public void processIngestion(UUID ingestionJobId) {
        IngestionJobEntity job = ingestionJobJpaRepository.findById(ingestionJobId)
                .orElseThrow(() -> new NotFoundException("Ingestion job not found"));

        try {
            updateJobStatus(job, IngestionJobStatus.VALIDATING, null);
            if (job.getStartedAt() == null) {
                job.setStartedAt(Instant.now());
                ingestionJobJpaRepository.save(job);
            }

            updateJobStatus(job, IngestionJobStatus.CLONING, null);
            RepositoryCloneService.CloneResult cloneResult = repositoryCloneService.cloneRepository(
                    job.getRepository().getId().toString(),
                    job.getId().toString(),
                    job.getSubmittedUrl()
            );
            job.setClonePath(cloneResult.clonePath());
            ingestionJobJpaRepository.save(job);

            updateJobStatus(job, IngestionJobStatus.ANALYZING, null);
            AnalysisServiceResponse analysis = analysisClientService.analyze(new AnalysisServiceRequest(
                    job.getId().toString(),
                    job.getRepository().getId().toString(),
                    cloneResult.clonePath(),
                    job.getSubmittedUrl()
            ));

            updateJobStatus(job, IngestionJobStatus.STORING, null);
            persistAnalysis(job, cloneResult, analysis);

            job.setFinishedAt(Instant.now());
            updateJobStatus(job, IngestionJobStatus.COMPLETED, null);
        } catch (Exception exception) {
            job.setFinishedAt(Instant.now());
            String errorMessage = exception.getMessage();
            if (errorMessage == null && exception.getCause() != null) {
                errorMessage = exception.getCause().getMessage();
            }
            updateJobStatus(job, IngestionJobStatus.FAILED, errorMessage);
        }
    }

    @Transactional
    protected void persistAnalysis(
            IngestionJobEntity job,
            RepositoryCloneService.CloneResult cloneResult,
            AnalysisServiceResponse analysis
    ) {
        RepositoryEntity repository = job.getRepository();
        repository.setStatus(IngestionJobStatus.COMPLETED.name());
        repository.setDefaultBranch(cloneResult.branchName());
        repository.setLastIngestedAt(Instant.now());
        repositoryJpaRepository.save(repository);

        RepositorySnapshotEntity snapshot = new RepositorySnapshotEntity();
        snapshot.setRepository(repository);
        snapshot.setIngestionJob(job);
        snapshot.setBranchName(cloneResult.branchName());
        snapshot.setCommitSha(cloneResult.commitSha());
        snapshot.setCommitMessage(cloneResult.commitMessage());
        snapshot.setCommitAuthor(cloneResult.commitAuthor());
        snapshot.setCommittedAt(cloneResult.committedAt());
        snapshot.setRootDirectory(cloneResult.clonePath());
        snapshot.setTotalFiles(analysis.summary().totalFiles());
        snapshot.setTotalDirectories(analysis.summary().totalDirectories());
        snapshot.setLanguageSummaryJson(writeJson(analysis.summary().languageSummary()));
        repositorySnapshotJpaRepository.save(snapshot);

        Map<String, FileEntity> filesByPath = new HashMap<>();
        for (AnalysisServiceResponse.DirectoryItem item : analysis.directories()) {
            DirectoryEntity entity = new DirectoryEntity();
            entity.setSnapshot(snapshot);
            entity.setRelativePath(item.relativePath());
            entity.setName(item.name());
            entity.setParentPath(item.parentPath());
            directoryJpaRepository.save(entity);
        }

        for (AnalysisServiceResponse.FileItem item : analysis.files()) {
            FileEntity entity = new FileEntity();
            entity.setSnapshot(snapshot);
            entity.setRelativePath(item.relativePath());
            entity.setFileName(item.fileName());
            entity.setExtension(item.extension());
            entity.setLanguage(item.language());
            entity.setSizeBytes(item.sizeBytes());
            entity.setBinary(item.isBinary());
            entity.setDirectoryPath(parentPath(item.relativePath()));
            fileJpaRepository.save(entity);
            filesByPath.put(item.relativePath(), entity);
        }

        for (AnalysisServiceResponse.SymbolItem item : analysis.symbols()) {
            CodeSymbolEntity entity = new CodeSymbolEntity();
            entity.setSnapshot(snapshot);
            entity.setFile(filesByPath.get(item.relativePath()));
            entity.setSymbolType(item.symbolType());
            entity.setName(item.name());
            entity.setQualifiedName(item.qualifiedName());
            entity.setLanguage(item.language());
            entity.setStartLine(item.startLine());
            entity.setEndLine(item.endLine());
            entity.setParentSymbolName(item.parentSymbolName());
            codeSymbolJpaRepository.save(entity);
        }

        for (AnalysisServiceResponse.ImportItem item : analysis.imports()) {
            ImportRelationEntity entity = new ImportRelationEntity();
            entity.setSnapshot(snapshot);
            entity.setFile(filesByPath.get(item.relativePath()));
            entity.setImportValue(item.importValue());
            entity.setImportType(item.importType());
            entity.setResolvedPath(item.resolvedPath());
            importRelationJpaRepository.save(entity);
        }

        AnalysisResultEntity resultEntity = new AnalysisResultEntity();
        resultEntity.setIngestionJob(job);
        resultEntity.setSnapshot(snapshot);
        resultEntity.setResultVersion("phase-2");
        resultEntity.setPayloadJson(writeJson(analysis));
        analysisResultJpaRepository.save(resultEntity);
    }

    private RepositoryEntity createRepository(GithubRepoRef repoRef) {
        RepositoryEntity repository = new RepositoryEntity();
        repository.setGithubUrl(repoRef.normalizedUrl());
        repository.setOwner(repoRef.owner());
        repository.setName(repoRef.name());
        repository.setPublic(true);
        repository.setStatus(IngestionJobStatus.PENDING.name());
        return repositoryJpaRepository.save(repository);
    }

    private void updateJobStatus(IngestionJobEntity job, IngestionJobStatus status, String errorMessage) {
        job.setStatus(status.name());
        job.setErrorMessage(errorMessage);
        ingestionJobJpaRepository.save(job);
    }

    private IngestionJobResponse mapJob(IngestionJobEntity job) {
        return new IngestionJobResponse(
                job.getId().toString(),
                job.getRepository().getId().toString(),
                job.getStatus(),
                job.getErrorMessage(),
                formatInstant(job.getCreatedAt()),
                formatInstant(job.getStartedAt()),
                formatInstant(job.getFinishedAt())
        );
    }

    private RepositorySummaryResponse.LatestSnapshot mapSnapshot(RepositorySnapshotEntity snapshot) {
        return new RepositorySummaryResponse.LatestSnapshot(
                snapshot.getId().toString(),
                snapshot.getBranchName(),
                snapshot.getCommitSha(),
                snapshot.getTotalFiles(),
                snapshot.getTotalDirectories(),
                readLanguageSummary(snapshot.getLanguageSummaryJson())
        );
    }

    private RepositorySnapshotEntity latestSnapshotFor(String repositoryId) {
        RepositoryEntity repository = repositoryJpaRepository.findById(UUID.fromString(repositoryId))
                .orElseThrow(() -> new NotFoundException("Repository not found"));
        return repositorySnapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)
                .orElseThrow(() -> new NotFoundException("Repository snapshot not found"));
    }

    private String formatInstant(Instant instant) {
        return instant != null ? instant.toString() : null;
    }

    private String parentPath(String relativePath) {
        int index = relativePath.lastIndexOf('/');
        return index >= 0 ? relativePath.substring(0, index) : null;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize JSON payload", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readLanguageSummary(String json) {
        try {
            return objectMapper.readValue(json, Map.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize language summary", exception);
        }
    }
}
