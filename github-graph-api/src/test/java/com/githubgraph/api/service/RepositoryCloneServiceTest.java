package com.githubgraph.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.domain.ingestion.IngestionFailureCategory;
import com.githubgraph.api.exception.IngestionException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryCloneServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsAndRemovesSnapshotOverConfiguredSizeLimit() throws Exception {
        Path clonePath = temporaryDirectory.resolve("repository").resolve("job");
        Files.createDirectories(clonePath);
        Files.write(clonePath.resolve("large.bin"), new byte[128]);
        RepositoryCloneService service = new RepositoryCloneService(properties(64));

        IngestionException exception = assertThrows(
                IngestionException.class,
                () -> service.cloneRepository("repository", "job", "https://github.com/example/project")
        );

        assertEquals(IngestionFailureCategory.CLONE_SIZE_LIMIT, exception.getCategory());
        assertFalse(Files.exists(clonePath));
    }

    private AppProperties properties(long maxSizeBytes) {
        return new AppProperties(
                new AppProperties.Analysis("http://analysis"),
                new AppProperties.CloneProperties(temporaryDirectory.toString(), 10, maxSizeBytes, 50),
                new AppProperties.Github("https://api.github.com", ""),
                new AppProperties.Neo4jInitialization(3, 1, 4)
        );
    }
}
