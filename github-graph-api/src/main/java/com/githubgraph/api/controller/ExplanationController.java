package com.githubgraph.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.githubgraph.api.dto.explanation.GroundedExplanationRequest;
import com.githubgraph.api.service.ExplanationQueryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/explanations")
public class ExplanationController {

    private final ExplanationQueryService explanationQueryService;

    public ExplanationController(ExplanationQueryService explanationQueryService) {
        this.explanationQueryService = explanationQueryService;
    }

    @PostMapping("/query")
    public JsonNode query(@Valid @RequestBody GroundedExplanationRequest request) {
        return explanationQueryService.query(request);
    }
}
