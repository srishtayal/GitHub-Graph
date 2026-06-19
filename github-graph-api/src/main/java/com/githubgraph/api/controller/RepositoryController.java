package com.githubgraph.api.controller;

import com.githubgraph.api.dto.FileSummaryResponse;
import com.githubgraph.api.dto.ImportSummaryResponse;
import com.githubgraph.api.dto.RepositorySummaryResponse;
import com.githubgraph.api.dto.SymbolSummaryResponse;
import com.githubgraph.api.service.IngestionOrchestratorService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryController {

    private final IngestionOrchestratorService ingestionService;

    public RepositoryController(IngestionOrchestratorService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @GetMapping("/{repositoryId}")
    public RepositorySummaryResponse getRepository(@PathVariable String repositoryId) {
        return ingestionService.getRepositorySummary(repositoryId);
    }

    @GetMapping("/{repositoryId}/files")
    public FileSummaryResponse getFiles(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryFiles(repositoryId);
    }

    @GetMapping("/{repositoryId}/symbols")
    public SymbolSummaryResponse getSymbols(@PathVariable String repositoryId) {
        return ingestionService.getRepositorySymbols(repositoryId);
    }

    @GetMapping("/{repositoryId}/imports")
    public ImportSummaryResponse getImports(@PathVariable String repositoryId) {
        return ingestionService.getRepositoryImports(repositoryId);
    }
}
