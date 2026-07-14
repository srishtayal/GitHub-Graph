# Graph Analytics

This guide describes the approved design for Phase 5. The analytics layer is implemented in the Python analysis engine and operates on an in-memory Phase 4 `GraphPayload`; it does not query Neo4j or add frontend behavior.

See [Phase 5](../PHASE_5.md) for the full scope, graph contract, API plan, and test strategy.

## Directional meaning

Dependency edges point from a dependent node to the node it needs. DFS follows outgoing edges to trace dependencies. BFS follows incoming edges to identify nodes impacted by a failed dependency.

Containment (`BELONGS_TO`) is excluded by default. Internal file/module ordering uses `IMPORTS` and resolved internal `USES` edges; function calls are optional for cycle analysis but excluded from default topological sorting.

## Complexity

DFS, BFS, connected components, topological sort, degree centrality, and directed cycle detection are each `O(V + E)` for a selected graph projection.
