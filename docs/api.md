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
