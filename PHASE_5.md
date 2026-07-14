# GitHub Graph — Phase 5: Graph Analytics Layer

## Goal

Phase 5 adds a Python-only, in-memory analytics layer over the normalized graph emitted by Phase 4. It produces structured, deterministic results for dependency tracing, impact analysis, architectural grouping, dependency ordering, critical-node ranking, and circular-dependency detection.

This phase does not add frontend functionality, change graph persistence, or modify the Phase 3/4 graph schema.

## Scope

The analytics layer will implement:

- depth-first dependency tracing
- breadth-first impact analysis
- weakly connected component grouping
- topological sorting for internal file/module dependencies
- normalized degree centrality
- directed cycle detection for import/module and optional call-graph projections

The Python analysis engine is the only Phase 5 implementation target. Existing backend analytics code is outside this phase's scope.

## Input graph contract

The layer accepts the existing `GraphPayload` in memory:

```python
class GraphPayload(BaseModel):
    nodes: list[GraphNode]
    edges: list[GraphEdge]
```

Current graph node types are `repo`, `file`, `class`, `function`, `api`, and `module`. Methods are represented as `function` nodes with `functionType="method"` in their properties.

Current graph edge types are `BELONGS_TO`, `IMPORTS`, `USES`, `INHERITS`, and `CALLS`.

Stable graph node IDs are the sole query identifier for this phase. File paths and qualified names remain node metadata and are not alternate analytics query keys.

## Graph semantics

Dependency edges are directed from the dependent to the dependency:

```text
caller ──CALLS──> callee
importing file ──IMPORTS / USES──> imported module or file
child class ──INHERITS──> parent class
```

Therefore, outgoing traversal answers “what does this node depend on?” and incoming traversal answers “what depends on this node?” or “what may be affected if it fails?”.

`BELONGS_TO` is a containment edge, not a dependency edge. It is excluded from dependency algorithms by default so the repository root does not artificially connect otherwise independent areas.

## In-memory representation

An immutable graph projection will index the payload as:

```python
nodes_by_id: dict[str, GraphNode]
outgoing: dict[str, tuple[GraphEdge, ...]]
incoming: dict[str, tuple[GraphEdge, ...]]
```

Algorithms can request an edge-filtered projection without mutating the payload. Node IDs and edge IDs will be sorted before traversal to ensure deterministic result order.

External or unresolved modules may exist as `module` nodes. They remain visible in raw traversal when requested, but are excluded by default from internal dependency ordering and architectural analyses.

## Algorithm policy

| Capability | Default graph projection | Result intent |
| --- | --- | --- |
| DFS trace | outgoing dependency edges | full dependency chain |
| BFS impact | incoming dependency edges | dependent nodes affected by a failure |
| Components | undirected non-containment edges | isolated areas and structural clusters |
| Topological sort | internal `IMPORTS` and resolved internal `USES` | prerequisites-first processing/load order |
| Centrality | internal dependency edges | shared/high-impact nodes |
| Cycle detection | internal imports and resolved `USES`; `CALLS` optional | circular imports and recursion/call loops |

Topological sorting deliberately excludes `CALLS` by default. Call graphs frequently contain valid recursion and are not a reliable build or load order. If a cycle prevents a complete topological order, the result will include the partial order, blocked node IDs, and detected cycle paths.

Centrality uses normalized degree centrality in this phase:

```text
centrality = (in_degree + out_degree) / (eligible_node_count - 1)
```

The result also reports in-degree and out-degree so consumers can distinguish heavily reused dependencies from highly dependent orchestration nodes.

## Planned Python package layout

```text
github-graph-analysis/
  app/
    analytics/
      graph_projection.py
      models.py
      filters.py
      traversal.py
      components.py
      topology.py
      centrality.py
      cycles.py
    services/
      graph_analytics_service.py
  tests/
    fixtures/graph_fixtures.py
    test_graph_projection.py
    test_traversal.py
    test_components.py
    test_topology.py
    test_centrality.py
    test_cycles.py
```

## Public Python APIs

The following core APIs are planned. Each accepts stable node IDs and returns a typed, structured result rather than printing output.

```python
trace_dependencies(graph, start_node_id, *, direction="dependencies", edge_types=None,
                   max_depth=None, include_external=False)
analyze_impact(graph, start_node_id, *, edge_types=None, max_depth=None,
               include_external=False)
find_connected_components(graph, *, edge_types=None, include_external=False)
topological_sort(graph, *, edge_types=None, include_external=False)
rank_centrality(graph, *, edge_types=None, node_types=None, limit=None,
                include_external=False)
detect_cycles(graph, *, edge_types=None, include_external=False)
```

Result models will include relevant query/configuration metadata, selected edge types, visited or ranked nodes, depths and predecessor information when applicable, excluded-node reasoning, and cycle paths or ordering diagnostics where applicable.

## Verification strategy

Unit tests will use small, explicit `GraphPayload` fixtures and cover:

- deterministic DFS and BFS order, depth, predecessor, and maximum-depth behavior
- incoming-only impact traversal
- edge-type filtering
- isolated nodes and multiple connected components
- acyclic prerequisite-first topological ordering
- partial topological results when cycles are present
- self cycles, multi-node cycles, and cycle-path deduplication
- external module inclusion/exclusion
- centrality ranking and deterministic tie-breaking
- validation failures for unknown stable node IDs

## Schema changes

None. Phase 5 consumes the current normalized graph as-is.
