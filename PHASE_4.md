# GitHub Graph - Phase 4

This document records the implementation status and design for **Phase 4: Build the code graph**.

## Goal

Convert structured static-code extraction output into a persisted graph that can be queried and visualized.

## Status

Phase 4 is implemented with:

- graph payload generation in the Python analysis service
- persisted graph storage in **Neo4j**
- repository graph retrieval from **Neo4j** through backend APIs
- graph endpoint for frontend consumption

## Graph model

### Node types

- `repo`
- `file`
- `class`
- `function`
- `api`
- `module`

### Edge types

- `BELONGS_TO`
- `IMPORTS`
- `USES`
- `INHERITS`
- `CALLS`

## Storage strategy

The Python analysis service builds a graph payload during analysis. The Spring Boot backend stores the graph in Neo4j when ingestion reaches the `STORING` phase.

Each persisted Neo4j node is stored with:

- `repositoryId`
- `snapshotId`
- `id`
- `type`
- `label`
- graph-specific properties

Each persisted relationship is stored with:

- `repositoryId`
- `snapshotId`
- `edgeId`
- graph-specific properties

Neo4j uses a `CodeGraphNode` label with typed relationships such as `CALLS` and `IMPORTS`.

## Flow

1. Backend clones repository
2. Backend calls Python analysis service
3. Python extracts static code data
4. Python builds graph nodes and edges
5. Backend stores analysis payload in PostgreSQL
6. Backend stores graph nodes and edges in Neo4j
7. Backend serves `/api/v1/repositories/{repositoryId}/graph` from Neo4j

## Backend endpoints

### Repository graph

`GET /api/v1/repositories/{repositoryId}/graph`

Returns:

```json
{
  "nodes": [
    {
      "id": "repo:123",
      "type": "repo",
      "label": "owner/repo",
      "properties": {}
    }
  ],
  "edges": [
    {
      "id": "edge:abc",
      "source": "function:a",
      "target": "function:b",
      "type": "CALLS",
      "properties": {}
    }
  ]
}
```

## Neo4j schema initialization

The backend initializes a Neo4j uniqueness constraint:

- `CodeGraphNode(repositoryId, snapshotId, id)`

This keeps per-snapshot graph nodes stable and safely replaceable.

## Files added or updated for Phase 4

### Backend

- `github-graph-api/src/main/java/com/githubgraph/api/service/RepositoryGraphService.java`
- `github-graph-api/src/main/java/com/githubgraph/api/config/Neo4jSchemaInitializer.java`
- `github-graph-api/src/main/java/com/githubgraph/api/service/IngestionOrchestratorService.java`
- `github-graph-api/src/main/java/com/githubgraph/api/controller/RepositoryController.java`

### Analysis service

- `github-graph-analysis/app/services/graph_planner.py`
- `github-graph-analysis/app/api/routes/analysis.py`

## What Phase 4 does not include yet

- graph algorithms such as BFS, DFS, centrality, or cycle detection
- similarity ranking
- bug-localization heuristics
- rich graph UI exploration

Those belong to later phases.
