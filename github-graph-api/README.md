# github-graph-api

Spring Boot backend for GitHub Graph Phase 1.

## Responsibilities

- Expose REST APIs for ingestion and status
- Validate public GitHub repository URLs
- Orchestrate clone and analysis flow
- Persist relational state in PostgreSQL
- Coordinate graph writes to Neo4j
- Expose repository-scoped similarity, clustering, and failure localization
- Persist snapshot-scoped failure history and confirmed root causes

## Local development

```bash
mvn spring-boot:run
```

Configure PostgreSQL, Neo4j, analysis service URL, and clone root using environment variables from `.env.example`.

## Intelligence APIs

```text
GET   /api/v1/intelligence/similarity/{nodeId}?repositoryId=...
GET   /api/v1/intelligence/clusters?repositoryId=...
POST  /api/v1/intelligence/failures/localize
POST  /api/v1/repositories/{repositoryId}/failures
GET   /api/v1/repositories/{repositoryId}/failures
PATCH /api/v1/failures/{failureId}
```

Add `snapshotId` to select an exact repository snapshot. If omitted, the latest
snapshot is used. Public callers never submit a graph payload; the API reads it
from Neo4j and reads matching failure history from PostgreSQL.

## Grounded explanation API

```text
POST /api/v1/explanations/query
```

The request needs only `repositoryId` and `query`; `targetNodeId`, `stackTrace`,
and `errorLog` are optional. The API loads the latest graph and snapshot-scoped
failure history before calling Python orchestration.

```json
{
  "repositoryId": "2107ec31-c527-4321-8a51-49fddf8c45c9",
  "query": "What is the repository structure?"
}
```

Provider failures return `502 Bad Gateway`. An unavailable or unconfigured
analysis service returns `503 Service Unavailable`.

## Real-service integration test

From the repository root, run:

```bash
bash infra/run-stage2-integration.sh
```

This exercises the public intelligence and failure-history APIs against real
PostgreSQL, Neo4j, and Python analysis containers, including an API restart and
snapshot-isolation assertions.
