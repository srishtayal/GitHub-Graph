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
        List<Map<String, Object>> codeFiles,
        List<SymbolItem> symbols,
        List<ImportItem> imports,
        List<Map<String, Object>> classes,
        List<Map<String, Object>> functions,
        List<Map<String, Object>> methodCalls,
        List<Map<String, Object>> inheritance,
        List<Map<String, Object>> apiRoutes,
        List<Map<String, Object>> moduleDependencies,
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
            Map<String, Integer> languageSummary,
            int totalClasses,
            int totalFunctions,
            int totalMethodCalls,
            int totalApiRoutes,
            int totalModuleDependencies
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
