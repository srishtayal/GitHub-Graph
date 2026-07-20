package com.githubgraph.api.service;

import com.githubgraph.api.config.AppProperties;
import com.githubgraph.api.domain.ingestion.IngestionFailureCategory;
import com.githubgraph.api.exception.IngestionException;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RepositoryCloneService {

    private static final int MAX_ERROR_OUTPUT_LENGTH = 4_000;

    private final AppProperties properties;

    public RepositoryCloneService(AppProperties properties) {
        this.properties = properties;
    }

    public CloneResult cloneRepository(String repositoryId, String ingestionJobId, String githubUrl) {
        Path clonePath = Path.of(properties.cloneProperties().root(), repositoryId, ingestionJobId);
        Path cloneLogPath = clonePath.resolveSibling(ingestionJobId + ".clone.log");
        try {
            Files.createDirectories(clonePath.getParent());
            if (Files.exists(clonePath)) {
                enforceMaximumSize(clonePath);
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
            cloneProcessBuilder.environment().put("GIT_TERMINAL_PROMPT", "0");
            Process cloneProcess = cloneProcessBuilder
                    .redirectErrorStream(true)
                    .redirectOutput(cloneLogPath.toFile())
                    .start();
            monitorClone(cloneProcess, clonePath);
            int exitCode = cloneProcess.exitValue();
            if (exitCode != 0) {
                String output = readCloneOutput(cloneLogPath);
                deleteDirectory(clonePath);
                throw new IngestionException(
                        IngestionFailureCategory.CLONE_FAILED,
                        output.isBlank() ? "Git clone failed" : "Git clone failed: " + output
                );
            }

            enforceMaximumSize(clonePath);
            return buildMetadata(clonePath);
        } catch (IngestionException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            deleteDirectoryQuietly(clonePath);
            throw new IngestionException(
                    IngestionFailureCategory.CLONE_FAILED,
                    "Repository clone was interrupted",
                    exception
            );
        } catch (IOException exception) {
            deleteDirectoryQuietly(clonePath);
            throw new IngestionException(
                    IngestionFailureCategory.CLONE_FAILED,
                    "Failed to clone repository",
                    exception
            );
        } finally {
            try {
                Files.deleteIfExists(cloneLogPath);
            } catch (IOException ignored) {
                // Clone logs are best-effort diagnostics and must not fail the job.
            }
        }
    }

    private void monitorClone(Process process, Path clonePath) throws InterruptedException, IOException {
        long timeoutNanos = TimeUnit.SECONDS.toNanos(properties.cloneProperties().timeoutSeconds());
        long deadline = System.nanoTime() + timeoutNanos;
        long pollInterval = Math.max(50, properties.cloneProperties().pollIntervalMillis());

        while (!process.waitFor(pollInterval, TimeUnit.MILLISECONDS)) {
            if (System.nanoTime() >= deadline) {
                terminate(process);
                deleteDirectory(clonePath);
                throw new IngestionException(
                        IngestionFailureCategory.CLONE_TIMEOUT,
                        "Repository clone exceeded the "
                                + properties.cloneProperties().timeoutSeconds()
                                + " second timeout"
                );
            }
            if (directorySize(clonePath) > properties.cloneProperties().maxSizeBytes()) {
                terminate(process);
                deleteDirectory(clonePath);
                throw new IngestionException(
                        IngestionFailureCategory.CLONE_SIZE_LIMIT,
                        "Repository clone exceeded the "
                                + properties.cloneProperties().maxSizeBytes()
                                + " byte size limit"
                );
            }
        }
    }

    private void enforceMaximumSize(Path clonePath) throws IOException {
        if (directorySize(clonePath) > properties.cloneProperties().maxSizeBytes()) {
            deleteDirectory(clonePath);
            throw new IngestionException(
                    IngestionFailureCategory.CLONE_SIZE_LIMIT,
                    "Repository clone exceeded the "
                            + properties.cloneProperties().maxSizeBytes()
                            + " byte size limit"
            );
        }
    }

    private long directorySize(Path root) throws IOException {
        if (!Files.exists(root)) {
            return 0;
        }
        AtomicLong total = new AtomicLong();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                total.addAndGet(attributes.size());
                return total.get() > properties.cloneProperties().maxSizeBytes()
                        ? FileVisitResult.TERMINATE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exception) {
                return FileVisitResult.CONTINUE;
            }
        });
        return total.get();
    }

    private void terminate(Process process) throws InterruptedException {
        process.destroy();
        if (!process.waitFor(2, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
    }

    private String readCloneOutput(Path cloneLogPath) throws IOException {
        if (!Files.exists(cloneLogPath)) {
            return "";
        }
        String output = Files.readString(cloneLogPath).trim();
        return output.length() <= MAX_ERROR_OUTPUT_LENGTH
                ? output
                : output.substring(0, MAX_ERROR_OUTPUT_LENGTH);
    }

    private void deleteDirectoryQuietly(Path root) {
        try {
            deleteDirectory(root);
        } catch (IOException ignored) {
            // Preserve the original failure; a later ingestion uses a new job path.
        }
    }

    private void deleteDirectory(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                if (exception != null) {
                    throw exception;
                }
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
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
