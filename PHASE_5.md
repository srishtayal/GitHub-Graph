# GitHub Graph - Phase 5

Phase 5 adds a graph analytics layer on top of the Phase 4 dependency graph.

## Algorithms

The analysis service applies:

- **DFS** for deep dependency tracing from a selected node
- **BFS** for upstream impact spread (what depends on this node)
- **Connected components** for tightly coupled module grouping
- **Topological sort** for dependency ordering and acyclicity checks
- **Centrality** for critical files and functions
- **Cycle detection** for circular dependency discovery

## Endpoints

### Analysis service (internal)

`POST /internal/v1/graph-analytics`

Request body:

```json
{
  "graph": {
    "nodes": [],
    "edges": []
  },
  "nodeId": "api.py",
  "maxDepth": 10
}
```

Response sections:

- `insights` - actionable summaries
- `connected_components`
- `topological_sort`
- `centrality`
- `cycles`
- `node_analysis` - present when `nodeId` is supplied

### Backend API (public)

`GET /api/v1/repositories/{repositoryId}/analytics`

Optional query params:

- `nodeId` - node id or label (for example `api.py`)
- `maxDepth` - traversal depth (default `10`)

The backend loads the stored graph from PostgreSQL and delegates analytics to the Python service.

## Insight examples

The `insights` object answers questions such as:

- which functions are most critical
- which files are most connected
- what depends on a selected node
- how far failure can spread upstream
- which parts of the repo are tightly coupled
- whether circular dependencies exist
