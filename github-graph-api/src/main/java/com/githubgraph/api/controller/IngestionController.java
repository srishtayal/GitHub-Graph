package com.githubgraph.api.controller;

import com.githubgraph.api.dto.CreateIngestionRequest;
import com.githubgraph.api.dto.CreateIngestionResponse;
import com.githubgraph.api.dto.IngestionJobResponse;
import com.githubgraph.api.service.IngestionOrchestratorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class IngestionController {

    private final IngestionOrchestratorService ingestionService;

    public IngestionController(IngestionOrchestratorService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/repositories/ingestions")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateIngestionResponse createIngestion(@Valid @RequestBody CreateIngestionRequest request) {
        return ingestionService.createIngestion(request);
    }

    @GetMapping("/ingestion-jobs/{jobId}")
    public IngestionJobResponse getIngestionJob(@PathVariable String jobId) {
        return ingestionService.getJob(jobId);
    }

    @PostMapping("/ingestion-jobs/{jobId}/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CreateIngestionResponse retryIngestion(@PathVariable String jobId) {
        return ingestionService.retryJob(jobId);
    }
}
