package com.githubgraph.api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.neo4j.core.Neo4jClient;

class Neo4jSchemaInitializerTest {

    @Test
    void retriesWithBoundedExponentialBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        Neo4jSchemaInitializer initializer = initializer(attempts, delays, 4, 10, 15, 2);

        initializer.initialize();

        assertEquals(3, attempts.get());
        assertEquals(List.of(10L, 15L), delays);
    }

    @Test
    void failsAfterConfiguredMaximumAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        Neo4jSchemaInitializer initializer = initializer(attempts, delays, 3, 5, 20, Integer.MAX_VALUE);

        assertThrows(IllegalStateException.class, initializer::initialize);
        assertEquals(3, attempts.get());
        assertEquals(List.of(5L, 10L), delays);
    }

    private Neo4jSchemaInitializer initializer(
            AtomicInteger attempts,
            List<Long> delays,
            int maxAttempts,
            long initialBackoff,
            long maxBackoff,
            int failuresBeforeSuccess
    ) {
        return new Neo4jSchemaInitializer(
                mock(Neo4jClient.class),
                maxAttempts,
                initialBackoff,
                maxBackoff,
                delays::add
        ) {
            @Override
            void createConstraint() {
                if (attempts.getAndIncrement() < failuresBeforeSuccess) {
                    throw new IllegalStateException("Neo4j is not ready");
                }
            }
        };
    }
}
