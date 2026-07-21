# Analytics API

## Repository graph projections

Base path:

`/api/v1/repositories/{repositoryId}/graph`

The projection endpoints resolve the repository's latest accessible snapshot
and build compact server-side views. The browser does not need to download the
complete raw Neo4j graph.

```text
GET /views/overview
GET /views/components/{componentId}
GET /views/files/{fileId}
GET /neighborhood/{nodeId}?depth=2
```

Projection levels have bounded suggested sizes: overview 15 nodes, component
40 nodes, file 80 nodes, and neighborhood 200 nodes with depth limited to 0-5.
The `truncated` field reports when a safety cap was reached.

Every response includes:

- repository and snapshot IDs;
- projection level and expandable root ID;
- raw and projected node/edge totals;
- node file, class, function, and route counts;
- incoming/outgoing dependency counts and normalized criticality;
- representative raw nodes and complete underlying node IDs;
- aggregated edges with per-type counts and underlying raw edge IDs.

Overview components are deterministic. Source files are grouped by `src/`
packages, conventional Python packages, or top-level directories. `tests/`,
`docs/`, and build/configuration files receive dedicated groups; very small or
overflow groups are combined deterministically. Internal group relationships
are omitted from the parent view.

Base path:

`/api/v1/analytics`

## Impact analysis

`GET /impact/{nodeId}?repositoryId=<repositoryId>`

Returns:

- start node
- affected nodes
- depth from root
- predecessor
- edge type used

## Dependency path

`GET /path/{nodeId}?repositoryId=<repositoryId>`

Returns:

- DFS traversal order
- depth
- predecessor

## Connected components

`GET /components?repositoryId=<repositoryId>`

Returns:

- total number of components
- nodes in each component

## Cycle detection

`GET /cycles?repositoryId=<repositoryId>`

Returns:

- whether cycles exist
- total cycles
- cycle node paths

## Topological order

`GET /topological-order?repositoryId=<repositoryId>`

Returns:

- whether the dependency graph is acyclic
- topological order if valid
- cycle information if invalid

## Critical nodes

`GET /critical?repositoryId=<repositoryId>&limit=20`

Returns:

- node list sorted by importance
- in-degree
- out-degree
- total degree
- degree centrality

## Grounded AI explanations

Base path: `http://localhost:8000/internal/v1`

`POST /explanations`

Accepts the Phase 7 `ExplanationRequest`: a user `query`, a `repositoryId`, the
existing `GraphPayload`, and the relevant precomputed Phase 5 or Phase 6 result
(`dependencyTrace`, `impactAnalysis`, `centrality`, `similarityRanking`,
`similarityClusters`, `bugLocalization`, `cycleDetection`, or
`topologicalSort`).

The response contains the answer, cited evidence IDs, referenced graph node and
edge IDs, confidence, limitations, and follow-up suggestions. It does not
perform raw repository scanning.

`GEMINI_API_KEY` must be configured for evidence that requires Gemini. Requests
with missing required analytics return an `insufficient` response locally;
missing Gemini configuration returns HTTP 503.
