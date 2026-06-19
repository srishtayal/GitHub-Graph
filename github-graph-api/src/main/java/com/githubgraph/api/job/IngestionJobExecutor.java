package com.githubgraph.api.job;

import com.githubgraph.api.service.IngestionOrchestratorService;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class IngestionJobExecutor {

    private final IngestionOrchestratorService ingestionOrchestratorService;

    public IngestionJobExecutor(@Lazy IngestionOrchestratorService ingestionOrchestratorService) {
        this.ingestionOrchestratorService = ingestionOrchestratorService;
    }

    @Async("ingestionExecutor")
    public void processAsync(UUID ingestionJobId) {
        ingestionOrchestratorService.processIngestion(ingestionJobId);
    }
}
