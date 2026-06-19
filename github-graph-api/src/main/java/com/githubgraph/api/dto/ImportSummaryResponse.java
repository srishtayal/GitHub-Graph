package com.githubgraph.api.dto;

import java.util.List;

public record ImportSummaryResponse(
        List<ImportItem> items
) {
    public record ImportItem(
            String fileId,
            String importValue,
            String resolvedPath
    ) {
    }
}
