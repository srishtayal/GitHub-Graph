package com.githubgraph.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.intelligence.CreateFailureRequest;
import com.githubgraph.api.dto.intelligence.FailureCollectionResponse;
import com.githubgraph.api.dto.intelligence.FailureRecordResponse;
import com.githubgraph.api.dto.intelligence.UpdateFailureRequest;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.exception.ValidationException;
import com.githubgraph.api.persistence.entity.FailureEvidenceEntity;
import com.githubgraph.api.persistence.entity.FailurePathNodeEntity;
import com.githubgraph.api.persistence.entity.FailureRecordEntity;
import com.githubgraph.api.persistence.entity.FailureRootCauseNodeEntity;
import com.githubgraph.api.persistence.repository.FailureEvidenceJpaRepository;
import com.githubgraph.api.persistence.repository.FailurePathNodeJpaRepository;
import com.githubgraph.api.persistence.repository.FailureRecordJpaRepository;
import com.githubgraph.api.persistence.repository.FailureRootCauseNodeJpaRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FailureHistoryService {

    private static final Set<String> ALLOWED_STATUSES = Set.of("OPEN", "RESOLVED", "IGNORED");

    private final FailureRecordJpaRepository failureRecordJpaRepository;
    private final FailureEvidenceJpaRepository failureEvidenceJpaRepository;
    private final FailurePathNodeJpaRepository failurePathNodeJpaRepository;
    private final FailureRootCauseNodeJpaRepository failureRootCauseNodeJpaRepository;
    private final GraphLoaderService graphLoaderService;

    public FailureHistoryService(
            FailureRecordJpaRepository failureRecordJpaRepository,
            FailureEvidenceJpaRepository failureEvidenceJpaRepository,
            FailurePathNodeJpaRepository failurePathNodeJpaRepository,
            FailureRootCauseNodeJpaRepository failureRootCauseNodeJpaRepository,
            GraphLoaderService graphLoaderService
    ) {
        this.failureRecordJpaRepository = failureRecordJpaRepository;
        this.failureEvidenceJpaRepository = failureEvidenceJpaRepository;
        this.failurePathNodeJpaRepository = failurePathNodeJpaRepository;
        this.failureRootCauseNodeJpaRepository = failureRootCauseNodeJpaRepository;
        this.graphLoaderService = graphLoaderService;
    }

    @Transactional
    public FailureRecordResponse create(
            GraphLoaderService.LoadedGraph loaded,
            CreateFailureRequest request,
            JsonNode localization
    ) {
        validateEvidence(request.failingNodeId(), request.errorLog(), request.stackTrace(), request.failurePathNodeIds());

        FailureRecordEntity failure = new FailureRecordEntity();
        failure.setRepository(loaded.repository());
        failure.setSnapshot(loaded.snapshot());
        failure.setStatus("OPEN");
        failure.setFailingNodeId(request.failingNodeId());
        failure.setErrorLog(request.errorLog());
        failure.setOccurredAt(request.occurredAt());
        failureRecordJpaRepository.saveAndFlush(failure);

        JsonNode resolvedPath = localization.path("resolvedFailurePath");
        JsonNode signature = resolvedPath.path("errorSignature");

        FailureEvidenceEntity evidence = new FailureEvidenceEntity();
        evidence.setFailure(failure);
        evidence.setStackTrace(request.stackTrace());
        evidence.setExceptionType(textOrNull(signature, "exceptionType"));
        evidence.setMessageFingerprint(textOrNull(signature, "messageFingerprint"));
        failureEvidenceJpaRepository.save(evidence);

        List<String> resolvedNodeIds = stringArray(resolvedPath.path("nodeIds"));
        for (int position = 0; position < resolvedNodeIds.size(); position++) {
            FailurePathNodeEntity pathNode = new FailurePathNodeEntity();
            pathNode.setFailure(failure);
            pathNode.setNodeId(resolvedNodeIds.get(position));
            pathNode.setPosition(position);
            pathNode.setSource("LOCALIZATION");
            failurePathNodeJpaRepository.save(pathNode);
        }

        return mapFailure(failure, evidence, resolvedNodeIds, List.of(), localization);
    }

    @Transactional(readOnly = true)
    public FailureCollectionResponse list(String repositoryId, String snapshotId) {
        GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(repositoryId, snapshotId);
        List<FailureRecordResponse> failures = failureRecordJpaRepository
                .findByRepositoryAndSnapshotOrderByOccurredAtDesc(loaded.repository(), loaded.snapshot())
                .stream()
                .map(failure -> mapFailure(failure, null))
                .toList();
        return new FailureCollectionResponse(
                loaded.repository().getId().toString(),
                loaded.snapshot().getId().toString(),
                failures
        );
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> historicalFailures(GraphLoaderService.LoadedGraph loaded) {
        return failureRecordJpaRepository
                .findByRepositoryAndSnapshotOrderByOccurredAtDesc(loaded.repository(), loaded.snapshot())
                .stream()
                .map(this::toHistoricalFailure)
                .toList();
    }

    @Transactional
    public FailureRecordResponse update(String failureId, UpdateFailureRequest request) {
        FailureRecordEntity failure = failureRecordJpaRepository.findById(UUID.fromString(failureId))
                .orElseThrow(() -> new NotFoundException("Failure record not found"));

        if (request.status() != null) {
            String status = request.status().trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_STATUSES.contains(status)) {
                throw new ValidationException("INVALID_FAILURE_STATUS", "Failure status must be OPEN, RESOLVED, or IGNORED");
            }
            failure.setStatus(status);
            if ("RESOLVED".equals(status) && request.resolvedAt() == null && failure.getResolvedAt() == null) {
                failure.setResolvedAt(Instant.now());
            }
        }
        if (request.resolutionNotes() != null) {
            failure.setResolutionNotes(request.resolutionNotes());
        }
        if (request.resolvedAt() != null) {
            failure.setResolvedAt(request.resolvedAt());
        }

        if (request.confirmedRootCauseNodeIds() != null) {
            GraphLoaderService.LoadedGraph loaded = graphLoaderService.loadGraph(
                    failure.getRepository().getId().toString(),
                    failure.getSnapshot().getId().toString()
            );
            List<String> rootCauseIds = distinct(request.confirmedRootCauseNodeIds());
            for (String nodeId : rootCauseIds) {
                if (!loaded.graph().containsNode(nodeId)) {
                    throw new ValidationException(
                            "ROOT_CAUSE_NODE_NOT_FOUND",
                            "Root-cause node does not exist in the failure snapshot: " + nodeId
                    );
                }
            }
            failureRootCauseNodeJpaRepository.deleteByFailure(failure);
            failureRootCauseNodeJpaRepository.flush();
            for (String nodeId : rootCauseIds) {
                FailureRootCauseNodeEntity rootCause = new FailureRootCauseNodeEntity();
                rootCause.setFailure(failure);
                rootCause.setNodeId(nodeId);
                failureRootCauseNodeJpaRepository.save(rootCause);
            }
        }

        failureRecordJpaRepository.saveAndFlush(failure);
        return mapFailure(failure, null);
    }

    private Map<String, Object> toHistoricalFailure(FailureRecordEntity failure) {
        FailureEvidenceEntity evidence = failureEvidenceJpaRepository.findByFailure(failure).orElse(null);
        Map<String, Object> signature = new LinkedHashMap<>();
        signature.put("exceptionType", evidence != null ? evidence.getExceptionType() : null);
        signature.put("messageFingerprint", evidence != null ? evidence.getMessageFingerprint() : null);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("snapshotId", failure.getSnapshot().getId().toString());
        metadata.put("status", failure.getStatus());
        metadata.put("resolutionNotes", failure.getResolutionNotes());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("failureId", failure.getId().toString());
        result.put("repositoryId", failure.getRepository().getId().toString());
        result.put("occurredAt", failure.getOccurredAt());
        result.put("failurePathNodeIds", pathNodeIds(failure));
        result.put("errorSignature", signature);
        result.put("confirmedRootCauseNodeIds", rootCauseNodeIds(failure));
        result.put("metadata", metadata);
        return result;
    }

    private FailureRecordResponse mapFailure(FailureRecordEntity failure, JsonNode localization) {
        FailureEvidenceEntity evidence = failureEvidenceJpaRepository.findByFailure(failure).orElse(null);
        return mapFailure(
                failure,
                evidence,
                pathNodeIds(failure),
                rootCauseNodeIds(failure),
                localization
        );
    }

    private FailureRecordResponse mapFailure(
            FailureRecordEntity failure,
            FailureEvidenceEntity evidence,
            List<String> pathNodeIds,
            List<String> rootCauseNodeIds,
            JsonNode localization
    ) {
        return new FailureRecordResponse(
                failure.getId().toString(),
                failure.getRepository().getId().toString(),
                failure.getSnapshot().getId().toString(),
                failure.getStatus(),
                failure.getFailingNodeId(),
                failure.getErrorLog(),
                evidence != null ? evidence.getStackTrace() : null,
                new FailureRecordResponse.ErrorSignatureResponse(
                        evidence != null ? evidence.getExceptionType() : null,
                        evidence != null ? evidence.getMessageFingerprint() : null
                ),
                pathNodeIds,
                rootCauseNodeIds,
                failure.getResolutionNotes(),
                failure.getOccurredAt(),
                failure.getResolvedAt(),
                failure.getCreatedAt(),
                failure.getUpdatedAt(),
                localization
        );
    }

    private List<String> pathNodeIds(FailureRecordEntity failure) {
        return failurePathNodeJpaRepository.findByFailureOrderByPositionAsc(failure).stream()
                .map(FailurePathNodeEntity::getNodeId)
                .toList();
    }

    private List<String> rootCauseNodeIds(FailureRecordEntity failure) {
        return failureRootCauseNodeJpaRepository.findByFailureOrderByNodeIdAsc(failure).stream()
                .map(FailureRootCauseNodeEntity::getNodeId)
                .toList();
    }

    private void validateEvidence(
            String failingNodeId,
            String errorLog,
            String stackTrace,
            List<String> failurePathNodeIds
    ) {
        if (isBlank(failingNodeId)
                && isBlank(errorLog)
                && isBlank(stackTrace)
                && (failurePathNodeIds == null || failurePathNodeIds.isEmpty())) {
            throw new ValidationException(
                    "FAILURE_EVIDENCE_REQUIRED",
                    "At least one failure evidence field is required"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private List<String> stringArray(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return distinct(values);
    }

    private List<String> distinct(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
