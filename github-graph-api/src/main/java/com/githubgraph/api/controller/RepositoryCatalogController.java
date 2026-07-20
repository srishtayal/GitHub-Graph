package com.githubgraph.api.controller;

import com.githubgraph.api.dto.RepositoryCatalogResponse;
import com.githubgraph.api.dto.SnapshotHistoryResponse;
import com.githubgraph.api.service.RepositoryCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories")
public class RepositoryCatalogController {
    private final RepositoryCatalogService catalogService;

    public RepositoryCatalogController(RepositoryCatalogService catalogService) { this.catalogService = catalogService; }

    @GetMapping
    public RepositoryCatalogResponse listSaved() { return catalogService.listSaved(); }

    @GetMapping("/{repositoryId}/snapshots")
    public SnapshotHistoryResponse history(@PathVariable String repositoryId) { return catalogService.history(repositoryId); }
}
