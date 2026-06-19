package com.githubgraph.api.service;

import com.githubgraph.api.config.AppProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCloneService {

    private final AppProperties properties;

    public RepositoryCloneService(AppProperties properties) {
        this.properties = properties;
    }

    public CloneResult cloneRepository(String repositoryId, String ingestionJobId, String githubUrl) {
        Path clonePath = Path.of(properties.cloneProperties().root(), repositoryId, ingestionJobId);
        try {
            Files.createDirectories(clonePath.getParent());
            if (Files.exists(clonePath)) {
                return buildMetadata(clonePath);
            }

            ProcessBuilder cloneProcessBuilder = new ProcessBuilder(
                    "git",
                    "clone",
                    "--depth",
                    "1",
                    githubUrl,
                    clonePath.toString()
            );
            Process cloneProcess = cloneProcessBuilder.redirectErrorStream(true).start();
            int exitCode = cloneProcess.waitFor();
            if (exitCode != 0) {
                String output = new String(cloneProcess.getInputStream().readAllBytes());
                throw new IllegalStateException("git clone failed: " + output);
            }

            return buildMetadata(clonePath);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Failed to clone repository", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to clone repository", exception);
        }
    }

    private CloneResult buildMetadata(Path clonePath) throws IOException, InterruptedException {
        String branch = runGit(clonePath, "rev-parse", "--abbrev-ref", "HEAD");
        String commitSha = runGit(clonePath, "rev-parse", "HEAD");
        String commitMessage = runGit(clonePath, "log", "-1", "--pretty=%s");
        String commitAuthor = runGit(clonePath, "log", "-1", "--pretty=%an");
        String committedAt = runGit(clonePath, "log", "-1", "--pretty=%cI");

        return new CloneResult(
                clonePath.toString(),
                branch,
                commitSha,
                commitMessage,
                commitAuthor,
                Instant.parse(committedAt)
        );
    }

    private String runGit(Path clonePath, String... command) throws IOException, InterruptedException {
        String[] fullCommand = new String[command.length + 1];
        fullCommand[0] = "git";
        System.arraycopy(command, 0, fullCommand, 1, command.length);
        Process process = new ProcessBuilder(fullCommand)
                .directory(clonePath.toFile())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        String output = new String(process.getInputStream().readAllBytes()).trim();
        if (exitCode != 0) {
            throw new IllegalStateException("git command failed: " + output);
        }
        return output;
    }

    public record CloneResult(
            String clonePath,
            String branchName,
            String commitSha,
            String commitMessage,
            String commitAuthor,
            Instant committedAt
    ) {
    }
}
