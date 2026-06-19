package com.githubgraph.api.dto;

import java.util.List;

public record SymbolSummaryResponse(
        List<SymbolItem> items
) {
    public record SymbolItem(
            String symbolId,
            String fileId,
            String symbolType,
            String name,
            String qualifiedName,
            String language,
            Integer startLine,
            Integer endLine
    ) {
    }
}
