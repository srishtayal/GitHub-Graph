# Architecture

## Where Phase 5 fits

The system currently has:

1. repository ingestion
2. static code extraction
3. graph construction
4. graph persistence in Neo4j

Phase 5 sits on top of the persisted Neo4j graph and provides reusable graph algorithms through the backend.

## Layering

### Neo4j persistence

- `RepositoryGraphService`
- source of truth for repository graph nodes and edges

### Graph loading

- `GraphLoaderService`
- loads the latest repository snapshot graph
- converts raw persisted graph data into a reusable `GraphView`

### Analytics algorithms

- focused classes for BFS, DFS, components, cycles, topological sort, and centrality
- no REST or persistence logic inside algorithm classes

### Analytics orchestration

- `GraphAnalyticsService`
- validates input and delegates to algorithms

### API layer

- `AnalyticsController`
- exposes REST endpoints for each analysis capability

## Design goals

- readable by students
- reusable in later phases
- linear-time algorithms where applicable
- minimal duplication of traversal logic
