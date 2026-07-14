# Architecture

## Where Phase 5 fits

The system currently has:

1. repository ingestion
2. static code extraction
3. graph construction
4. graph persistence in Neo4j

Phase 5 consumes the normalized Phase 4 graph payload in the Python analysis engine and provides reusable in-memory graph algorithms. It does not add backend orchestration or persistence behavior in this phase.

## Layering

### Graph input

- the existing Phase 4 `GraphPayload`
- stable node IDs, nodes, and directed edges

### Graph projection

- Python adjacency indexes for outgoing and incoming edges
- filtered, deterministic views for individual algorithms

### Analytics algorithms

- focused Python modules for BFS, DFS, components, cycles, topological sort, and centrality
- no REST, Neo4j, or persistence logic inside algorithm modules

### Analytics orchestration

- Python service façade validates stable node IDs and delegates to algorithms
- returns structured result models for later integration

## Design goals

- readable by students
- reusable in later phases
- linear-time algorithms where applicable
- minimal duplication of traversal logic
