package com.githubgraph.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.intelligence.CreateFailureRequest;
import com.githubgraph.api.dto.intelligence.FailureCollectionResponse;
import com.githubgraph.api.dto.intelligence.FailureLocalizationRequest;
import com.githubgraph.api.dto.intelligence.FailureRecordResponse;
import com.githubgraph.api.dto.intelligence.UpdateFailureRequest;
import com.githubgraph.api.service.FailureHistoryService;
import com.githubgraph.api.service.IntelligenceService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IntelligenceController {

    private final IntelligenceService intelligenceService;
    private final FailureHistoryService failureHistoryService;

    public IntelligenceController(
            IntelligenceService intelligenceService,
            FailureHistoryService failureHistoryService
    ) {
        this.intelligenceService = intelligenceService;
        this.failureHistoryService = failureHistoryService;
    }

    @GetMapping("/intelligence/similarity/{nodeId}")
    public JsonNode similarity(
            @PathVariable String nodeId,
            @RequestParam String repositoryId,
            @RequestParam(required = false) String snapshotId,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return intelligenceService.similarity(repositoryId, snapshotId, nodeId, limit);
    }

    @GetMapping("/intelligence/clusters")
    public JsonNode clusters(
            @RequestParam String repositoryId,
            @RequestParam(required = false) String snapshotId,
            @RequestParam(defaultValue = "function") String nodeType,
            @RequestParam(defaultValue = "0.5") double threshold
    ) {
        return intelligenceService.clusters(repositoryId, snapshotId, nodeType, threshold);
    }

    @PostMapping("/intelligence/failures/localize")
    public JsonNode localize(@RequestBody FailureLocalizationRequest request) {
        return intelligenceService.localize(request);
    }

    @PostMapping("/repositories/{repositoryId}/failures")
    @ResponseStatus(HttpStatus.CREATED)
    public FailureRecordResponse createFailure(
            @PathVariable String repositoryId,
            @RequestBody CreateFailureRequest request
    ) {
        return intelligenceService.recordFailure(repositoryId, request);
    }

    @GetMapping("/repositories/{repositoryId}/failures")
    public FailureCollectionResponse listFailures(
            @PathVariable String repositoryId,
            @RequestParam(required = false) String snapshotId
    ) {
        return failureHistoryService.list(repositoryId, snapshotId);
    }

    @PatchMapping("/failures/{failureId}")
    public FailureRecordResponse updateFailure(
            @PathVariable String failureId,
            @RequestBody UpdateFailureRequest request
    ) {
        return failureHistoryService.update(failureId, request);
    }
}
