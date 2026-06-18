# github-graph-api

Spring Boot backend for GitHub Graph Phase 1.

## Responsibilities

- Expose REST APIs for ingestion and status
- Validate public GitHub repository URLs
- Orchestrate clone and analysis flow
- Persist relational state in PostgreSQL
- Coordinate graph writes to Neo4j

## Local development

```bash
mvn spring-boot:run
```

Configure PostgreSQL, Neo4j, analysis service URL, and clone root using environment variables from `.env.example`.
