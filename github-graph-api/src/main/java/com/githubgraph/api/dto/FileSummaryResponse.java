package com.githubgraph.api.dto;

import java.util.List;

public record FileSummaryResponse(
        List<FileItem> items
) {
    public record FileItem(
            String fileId,
            String relativePath,
            String language,
            long sizeBytes
    ) {
    }
}
