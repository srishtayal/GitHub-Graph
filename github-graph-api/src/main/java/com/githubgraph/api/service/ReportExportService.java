package com.githubgraph.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.githubgraph.api.exception.NotFoundException;
import com.githubgraph.api.persistence.entity.RepositoryEntity;
import com.githubgraph.api.persistence.entity.RepositorySnapshotEntity;
import com.githubgraph.api.persistence.repository.RepositorySnapshotJpaRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

@Service
public class ReportExportService {

    private final RepositoryCatalogService catalogService;
    private final RepositorySnapshotJpaRepository snapshotJpaRepository;
    private final RepositoryGraphService graphService;
    private final ObjectMapper objectMapper;

    public ReportExportService(
            RepositoryCatalogService catalogService,
            RepositorySnapshotJpaRepository snapshotJpaRepository,
            RepositoryGraphService graphService,
            ObjectMapper objectMapper
    ) {
        this.catalogService = catalogService;
        this.snapshotJpaRepository = snapshotJpaRepository;
        this.graphService = graphService;
        this.objectMapper = objectMapper;
    }

    public byte[] json(String repositoryId, String snapshotId) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report(repositoryId, snapshotId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to create JSON report", exception);
        }
    }

    public byte[] pdf(String repositoryId, String snapshotId) {
        Map<String, Object> report = report(repositoryId, snapshotId);
        @SuppressWarnings("unchecked")
        Map<String, Object> snapshot = (Map<String, Object>) report.get("snapshot");
        @SuppressWarnings("unchecked")
        Map<String, Object> graph = (Map<String, Object>) report.get("graphSummary");
        List<String> lines = List.of(
                "GitHub Graph repository report",
                "Repository: " + report.get("repositoryUrl"),
                "Generated: " + report.get("generatedAt"),
                "",
                "Snapshot",
                "Branch: " + snapshot.get("branchName"),
                "Commit: " + snapshot.get("commitSha"),
                "Files: " + snapshot.get("totalFiles") + " | Directories: " + snapshot.get("totalDirectories"),
                "",
                "Graph summary",
                "Nodes: " + graph.get("nodes") + " | Edges: " + graph.get("edges"),
                "",
                "This report is snapshot-scoped. Static extraction is currently Python-first."
        );
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            writeLines(document, lines);
            document.save(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create PDF report", exception);
        }
    }

    private Map<String, Object> report(String repositoryId, String snapshotId) {
        RepositoryEntity repository = catalogService.findRepository(repositoryId);
        catalogService.assertAccess(repository);
        RepositorySnapshotEntity snapshot = resolveSnapshot(repository, snapshotId);
        JsonNode graph = graphService.loadRepositoryGraph(repository.getId(), snapshot);

        Map<String, Object> snapshotData = new LinkedHashMap<>();
        snapshotData.put("snapshotId", snapshot.getId().toString());
        snapshotData.put("branchName", snapshot.getBranchName());
        snapshotData.put("commitSha", snapshot.getCommitSha());
        snapshotData.put("commitMessage", snapshot.getCommitMessage());
        snapshotData.put("commitAuthor", snapshot.getCommitAuthor());
        snapshotData.put("committedAt", format(snapshot.getCommittedAt()));
        snapshotData.put("analyzedAt", format(snapshot.getCreatedAt()));
        snapshotData.put("totalFiles", snapshot.getTotalFiles());
        snapshotData.put("totalDirectories", snapshot.getTotalDirectories());
        snapshotData.put("languageSummary", readMap(snapshot.getLanguageSummaryJson()));

        Map<String, Object> graphSummary = Map.of(
                "nodes", graph.path("nodes").size(),
                "edges", graph.path("edges").size()
        );
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("reportVersion", "phase-9");
        report.put("generatedAt", format(java.time.Instant.now()));
        report.put("repositoryId", repository.getId().toString());
        report.put("repositoryUrl", repository.getGithubUrl());
        report.put("snapshot", snapshotData);
        report.put("graphSummary", graphSummary);
        report.put("graph", graph);
        return report;
    }

    private RepositorySnapshotEntity resolveSnapshot(RepositoryEntity repository, String snapshotId) {
        if (snapshotId == null || snapshotId.isBlank()) {
            return snapshotJpaRepository.findTopByRepositoryOrderByCreatedAtDesc(repository)
                    .orElseThrow(() -> new NotFoundException("Repository snapshot not found"));
        }
        return snapshotJpaRepository.findByIdAndRepository(UUID.fromString(snapshotId), repository)
                .orElseThrow(() -> new NotFoundException("Repository snapshot not found"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Integer> readMap(String value) {
        try { return objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException exception) { return Map.of(); }
    }

    private String format(java.time.Instant instant) {
        return instant == null ? "unknown" : DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private void writeLines(PDDocument document, List<String> rawLines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        float y = 790;
        PDPageContentStream content = new PDPageContentStream(document, page);
        content.setFont(PDType1Font.HELVETICA, 11);
        content.beginText();
        content.newLineAtOffset(48, y);
        for (String rawLine : rawLines) {
            for (String line : wrap(rawLine, 95)) {
                if (y < 55) {
                    content.endText();
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    content.setFont(PDType1Font.HELVETICA, 11);
                    content.beginText();
                    y = 790;
                    content.newLineAtOffset(48, y);
                }
                content.showText(line);
                content.newLineAtOffset(0, -15);
                y -= 15;
            }
        }
        content.endText();
        content.close();
    }

    private List<String> wrap(String value, int width) {
        if (value.length() <= width) return List.of(value);
        List<String> result = new ArrayList<>();
        String remaining = value;
        while (remaining.length() > width) {
            int breakAt = remaining.lastIndexOf(' ', width);
            if (breakAt <= 0) breakAt = width;
            result.add(remaining.substring(0, breakAt));
            remaining = remaining.substring(breakAt).trim();
        }
        result.add(remaining);
        return result;
    }
}
