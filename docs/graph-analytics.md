# Graph Analytics

This document explains the Phase 5 graph analytics layer in GitHub Graph.

## Scope

Phase 5 turns the stored dependency graph into a reusable analysis engine. It does not include AI, bug localization, or frontend visualization.

## Algorithms

### Breadth First Search (BFS)

Purpose:

- impact analysis
- downstream affected-node discovery

Behavior:

- starts from a node
- traverses incoming dependency edges
- returns all nodes affected by the selected node failing

Complexity:

- `O(V + E)`

### Depth First Search (DFS)

Purpose:

- dependency tracing
- deep execution-chain exploration

Behavior:

- starts from a node
- traverses outgoing dependency edges
- returns a DFS traversal order with parent/depth information

Complexity:

- `O(V + E)`

### Connected Components

Purpose:

- identify isolated subgraphs
- find naturally grouped code regions

Behavior:

- uses the structural graph excluding the repository root node
- treats edges as undirected for grouping

Complexity:

- `O(V + E)`

### Cycle Detection

Purpose:

- detect circular imports
- find recursive dependency loops
- return cycle paths

Behavior:

- runs DFS on the dependency graph
- tracks active recursion stack
- deduplicates discovered cycles

Complexity:

- `O(V + E)`

### Topological Sort

Purpose:

- build ordering
- architecture understanding
- dependency ordering

Behavior:

- runs on the dependency graph
- returns a valid order when acyclic
- returns a structured failure message and cycle hints when cyclic

Complexity:

- `O(V + E)`

### Centrality Analysis

Purpose:

- identify critical functions
- find bottleneck modules
- rank heavily reused utilities

Metrics:

- in-degree
- out-degree
- total degree
- normalized degree centrality

Complexity:

- `O(V + E)`

## Inputs

Each algorithm operates on an in-memory graph projection built from Neo4j:

- nodes by id
- outgoing adjacency
- incoming adjacency

## Outputs

The analytics endpoints return structured JSON suitable for later phases such as AI explanations and UI visualization.
