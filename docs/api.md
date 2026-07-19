# Analytics API

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
