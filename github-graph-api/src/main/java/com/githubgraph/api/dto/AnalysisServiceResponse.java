package com.githubgraph.api.dto;

import java.util.List;
import java.util.Map;

public record AnalysisServiceResponse(
        String ingestionJobId,
        String status,
        Snapshot snapshot,
        Summary summary,
        List<DirectoryItem> directories,
        List<FileItem> files,
        List<SymbolItem> symbols,
        List<ImportItem> imports,
        Graph graph
) {
    public record Snapshot(
            String branchName,
            String commitSha
    ) {
    }

    public record Summary(
            int totalFiles,
            int totalDirectories,
            Map<String, Integer> languageSummary
    ) {
    }

    public record DirectoryItem(
            String relativePath,
            String name,
            String parentPath
    ) {
    }

    public record FileItem(
            String relativePath,
            String fileName,
            String extension,
            String language,
            long sizeBytes,
            boolean isBinary
    ) {
    }

    public record SymbolItem(
            String relativePath,
            String symbolType,
            String name,
            String qualifiedName,
            String language,
            Integer startLine,
            Integer endLine,
            String parentSymbolName
    ) {
    }

    public record ImportItem(
            String relativePath,
            String importValue,
            String importType,
            String resolvedPath
    ) {
    }

    public record Graph(
            List<Map<String, Object>> nodes,
            List<Map<String, Object>> edges
    ) {
    }
}
