package com.githubgraph.api.controller;

import com.githubgraph.api.service.ReportExportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/repositories/{repositoryId}/exports")
public class ReportExportController {
    private final ReportExportService reportExportService;

    public ReportExportController(ReportExportService reportExportService) { this.reportExportService = reportExportService; }

    @GetMapping(value = "/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> json(@PathVariable String repositoryId, @RequestParam(required = false) String snapshotId) {
        return download(reportExportService.json(repositoryId, snapshotId), "application/json", "github-graph-report.json");
    }

    @GetMapping(value = "/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable String repositoryId, @RequestParam(required = false) String snapshotId) {
        return download(reportExportService.pdf(repositoryId, snapshotId), MediaType.APPLICATION_PDF_VALUE, "github-graph-report.pdf");
    }

    private ResponseEntity<byte[]> download(byte[] payload, String mediaType, String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mediaType))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(payload);
    }
}
