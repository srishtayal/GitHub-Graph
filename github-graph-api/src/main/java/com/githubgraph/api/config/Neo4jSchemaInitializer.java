package com.githubgraph.api.config;

import jakarta.annotation.PostConstruct;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

@Component
public class Neo4jSchemaInitializer {

    private final Neo4jClient neo4jClient;

    public Neo4jSchemaInitializer(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    @PostConstruct
    public void initialize() {
        neo4jClient.query("""
                CREATE CONSTRAINT code_graph_node_identity IF NOT EXISTS
                FOR (n:CodeGraphNode)
                REQUIRE (n.repositoryId, n.snapshotId, n.id) IS UNIQUE
                """).run();
    }
}
