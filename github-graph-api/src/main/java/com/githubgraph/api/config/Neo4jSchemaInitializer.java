package com.githubgraph.api.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class Neo4jSchemaInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(Neo4jSchemaInitializer.class);

    private final Neo4jClient neo4jClient;
    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final long maxBackoffMillis;
    private final Sleeper sleeper;

    @Autowired
    public Neo4jSchemaInitializer(Neo4jClient neo4jClient, AppProperties properties) {
        this(
                neo4jClient,
                properties.neo4jInitialization().maxAttempts(),
                properties.neo4jInitialization().initialBackoffMillis(),
                properties.neo4jInitialization().maxBackoffMillis(),
                Thread::sleep
        );
    }

    Neo4jSchemaInitializer(
            Neo4jClient neo4jClient,
            int maxAttempts,
            long initialBackoffMillis,
            long maxBackoffMillis,
            Sleeper sleeper
    ) {
        this.neo4jClient = neo4jClient;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(0, initialBackoffMillis);
        this.maxBackoffMillis = Math.max(this.initialBackoffMillis, maxBackoffMillis);
        this.sleeper = sleeper;
    }

    @PostConstruct
    public void initialize() {
        long backoffMillis = initialBackoffMillis;
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                createConstraint();
                return;
            } catch (RuntimeException exception) {
                lastFailure = exception;
                if (attempt == maxAttempts) {
                    break;
                }
                LOGGER.warn(
                        "Neo4j schema initialization attempt {}/{} failed; retrying in {} ms",
                        attempt,
                        maxAttempts,
                        backoffMillis
                );
                sleep(backoffMillis);
                backoffMillis = nextBackoff(backoffMillis);
            }
        }
        throw new IllegalStateException(
                "Neo4j schema initialization failed after " + maxAttempts + " attempts",
                lastFailure
        );
    }

    void createConstraint() {
        neo4jClient.query("""
                CREATE CONSTRAINT code_graph_node_identity IF NOT EXISTS
                FOR (n:CodeGraphNode)
                REQUIRE (n.repositoryId, n.snapshotId, n.id) IS UNIQUE
                """).run();
    }

    private void sleep(long delayMillis) {
        try {
            sleeper.sleep(delayMillis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Neo4j schema initialization retry was interrupted", exception);
        }
    }

    private long nextBackoff(long currentBackoffMillis) {
        if (currentBackoffMillis >= maxBackoffMillis) {
            return maxBackoffMillis;
        }
        return Math.min(maxBackoffMillis, currentBackoffMillis * 2);
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long delayMillis) throws InterruptedException;
    }
}
