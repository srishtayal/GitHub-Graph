package com.githubgraph.api.dto;

import java.util.List;

public record RepositoryCatalogResponse(List<RepositorySummaryResponse> repositories) {
}
