package com.githubgraph.api.service;

import com.githubgraph.api.dto.CreateIngestionRequest;
import com.githubgraph.api.dto.CreateIngestionResponse;
import com.githubgraph.api.dto.IngestionJobResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class IngestionService {

    private final Map<String, IngestionJobResponse> jobs = new ConcurrentHashMap<>();

    public CreateIngestionResponse createIngestion(CreateIngestionRequest request) {
        String repositoryId = UUID.nameUUIDFromBytes(request.githubUrl().getBytes()).toString();
        String jobId = UUID.randomUUID().toString();

        IngestionJobResponse job = new IngestionJobResponse(
                jobId,
                repositoryId,
                "PENDING",
                null
        );
        jobs.put(jobId, job);

        return new CreateIngestionResponse(jobId, repositoryId, "PENDING");
    }

    public IngestionJobResponse getJob(String jobId) {
        return jobs.getOrDefault(jobId, new IngestionJobResponse(jobId, null, "UNKNOWN", "Job not found"));
    }
}
