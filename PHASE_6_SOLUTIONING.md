# GitHub Graph - Phase 6 Solutioning

This document defines the design for Phase 6, the **Similarity and Bug-Localization Engine**, before implementation begins.

## Scope

Phase 6 extends the existing Python analysis engine with reusable, graph-based analysis services. It will:

1. Compare functions, files, modules, and failure paths using weighted Jaccard similarity.
2. Rank nodes of the same type by similarity.
3. Build transitive similarity clusters from threshold-qualified node pairs.
4. Resolve failure evidence from a failing node, stack trace, error log, or supplied path.
5. Compare current failures to historical failures.
6. Rank root-cause candidates with explainable evidence and confidence values.
7. Return structured, JSON-ready Pydantic results.

Phase 6 does **not** add frontend work, FastAPI endpoints, or persistent database storage. Historical failures are accessed through a persistence contract and initially supplied by local JSON fixtures.

## Constraints and Decisions

- The existing Phase 3 graph vocabulary is authoritative.
- Existing graph node types are: `repo`, `file`, `class`, `function`, `api`, and `module`.
- Existing graph edge types are: `BELONGS_TO`, `IMPORTS`, `CALLS`, `USES`, and `INHERITS`.
- Similarity rankings compare candidates only with the target's node type.
- Similarity uses configurable feature weights.
- Clustering uses connected components over an undirected threshold-qualified similarity graph. Clusters are therefore transitive.
- The graph schema will not change for Phase 6.
- External modules and unresolved stack-trace references remain separate from repository graph nodes. They are returned as unresolved evidence when appropriate.

## Existing Graph Model

Phase 3 produces `GraphPayload` with stable node IDs and graph properties such as file path, qualified name, source lines, and module name. Phase 6 reads this immutable payload; it does not mutate it.

Important existing relationships:

- `function -> function|module` through `CALLS`
- `file -> module` through `IMPORTS`
- `file -> file` through `USES` for resolved internal dependencies
- `function|class|file|api -> parent` through `BELONGS_TO`
- `class -> class|module` through `INHERITS`
- `api -> function` through `USES` when the API handler resolves

The feature extractor will centralize knowledge of this vocabulary so the rest of Phase 6 is isolated from graph traversal details.

## Proposed Folder Structure

```text
github-graph-analysis/
├── app/
│   ├── schemas/
│   │   ├── similarity.py
│   │   └── failure_analysis.py
│   └── services/
│       ├── graph_feature_extractor.py
│       ├── similarity_engine.py
│       ├── similarity_clustering.py
│       ├── failure_path_parser.py
│       ├── bug_localizer.py
│       └── failure_history_store.py
├── tests/
│   ├── fixtures/
│   │   ├── phase6_graphs.py
│   │   └── phase6_failures.json
│   ├── test_graph_feature_extractor.py
│   ├── test_similarity_engine.py
│   ├── test_similarity_clustering.py
│   ├── test_failure_path_parser.py
│   └── test_bug_localizer.py
└── PHASE_6.md
```

`PHASE_6.md` will become implementation-facing documentation. This file remains the design record.

## Architecture and Data Flow

```text
Phase 3 GraphPayload
        |
        v
GraphFeatureExtractor
  - validates nodes and edge endpoints
  - builds incoming/outgoing adjacency indexes
  - derives typed feature sets
        |
        +-------------------------------+
        |                               |
        v                               v
SimilarityEngine                  FailurePathParser
  - weighted Jaccard                - resolves node IDs
  - same-type ranking               - resolves stack frames
  - matched-feature evidence        - records unresolved references
        |                               |
        v                               v
SimilarityClusterer               BugLocalizer
  - threshold links                  - finds impacted nodes
  - connected components             - matches failure history
  - deterministic cluster IDs        - ranks suspects
        |                               |
        +---------------+---------------+
                        v
           JSON-ready structured result models
```

## Graph Feature Extraction

Each derived feature is a set of namespaced stable identifiers. Namespacing prevents a file, module, and function with equal labels from being treated as the same feature.

Examples:

```text
call:function:abc123
caller:function:def456
import:module:requests
dependency:file:789abc
neighbor:class:012def
file:services/auth.py
error:ValueError
```

The extractor will derive the following sets:

| Node type | Feature families |
|---|---|
| `function` | directly called nodes, direct caller nodes, incident graph neighbors, imports of its enclosing file |
| `file` | imported modules, resolved internal file dependencies, contained class/function symbols, incident graph neighbors |
| `module` | importing files, files that use the module, incident graph neighbors |
| failure path | involved graph nodes, touched files, dependencies, normalized error signature |

`class`, `api`, and `repo` remain useful contextual graph nodes, but are not ranked as similarity targets in the initial Phase 6 scope.

## Jaccard Similarity

For a feature family represented by sets `A` and `B`:

```text
J(A, B) = |A intersection B| / |A union B|
```

If both sets are empty, the similarity is `0.0`. Two nodes with no observable evidence must not be reported as identical.

Each comparison returns a per-feature score and the actual intersection used as evidence. The aggregate is a normalized weighted average over only feature families that are enabled and available to the comparison:

```text
aggregate_score = sum(weight_i * score_i) / sum(weight_i for included feature families)
```

### Approved default feature weights

```json
{
  "function": {
    "calledNodes": 0.40,
    "neighborNodes": 0.25,
    "callerNodes": 0.20,
    "enclosingFileImports": 0.15
  },
  "file": {
    "importedModules": 0.40,
    "internalDependencies": 0.30,
    "containedSymbols": 0.20,
    "neighborNodes": 0.10
  },
  "module": {
    "importingFiles": 0.45,
    "usingFiles": 0.35,
    "neighborNodes": 0.20
  },
  "failurePath": {
    "pathNodes": 0.45,
    "touchedFiles": 0.20,
    "dependencies": 0.20,
    "errorSignature": 0.15
  }
}
```

Weights will be held in a configuration model rather than hardcoded inside scoring logic. Validation will require non-negative weights and at least one positive weight for each enabled profile.

## Similarity Ranking

`SimilarityEngine.rank_similar` will:

1. Validate that the target exists.
2. Select graph nodes of exactly the target type.
3. Exclude the target itself.
4. Extract comparable features and compute weighted similarity.
5. Sort by descending aggregate score and then ascending candidate node ID for deterministic ties.
6. Apply the requested limit.
7. Attach cluster ID when clustering data has been supplied or computed.

Each result includes the target node, candidate node, aggregate score, per-feature scores, and matched features.

## Clustering

For one node type and one feature profile:

1. Compare every unordered pair of nodes.
2. Add an undirected similarity link for scores greater than or equal to the configured threshold.
3. Compute connected components of the similarity-link graph.
4. Assign each component a deterministic ID derived from its node type and sorted member node IDs.

This intentionally permits transitive clusters: if A is similar to B and B is similar to C at the threshold, all three belong to one cluster even when A and C are not directly similar enough.

Cluster output retains pairwise links so consumers can inspect why nodes were grouped.

## Failure Inputs and Resolution

The bug-localization service accepts one or more of:

- `failingNodeId`
- `stackTrace`
- `errorLog`
- `failurePathNodeIds`, ordered from entry point to observed failure where available

Resolution rules:

- Supplied IDs are accepted only when they exist in the graph.
- Stack-frame file paths and line numbers are matched to functions whose source range contains the line.
- Qualified function names are used where present to disambiguate symbols.
- Ambiguous and unknown references are not forced into graph nodes.
- External module or third-party frames are reported under `unresolvedReferences`.

The resolved path stores both ordered nodes for explanation and set-based features for similarity scoring.

## Historical Failure Contract

History is accessed through an interface, allowing local fixtures now and durable storage later.

```python
class FailureHistoryStore(Protocol):
    def list_for_repository(self, repository_id: str) -> list[HistoricalFailure]: ...
```

Initial local JSON records will have this logical shape:

```json
{
  "failureId": "incident-42",
  "repositoryId": "repo-123",
  "occurredAt": "2026-07-17T10:30:00Z",
  "failurePathNodeIds": ["function:...", "module:..."],
  "errorSignature": {
    "exceptionType": "ValueError",
    "messageFingerprint": "invalid-token"
  },
  "confirmedRootCauseNodeIds": ["function:..."],
  "metadata": {}
}
```

`confirmedRootCauseNodeIds` is optional. It provides useful supervision/evidence when known but is not required to compare failures.

## Bug Localization

The localizer will build an impacted region from:

- nodes on the resolved failure path;
- nodes resolved from stack frames;
- enclosing file and class nodes;
- direct callers and callees;
- direct dependency neighbors;
- bounded graph traversal around the failing node or resolved path.

It will compare the current normalized failure path against each historical failure using the failure-path similarity profile. The root-cause score is evidence-based and returned with its contributions. Its configurable default contribution families are:

| Evidence family | Purpose |
|---|---|
| current failure path | favors nodes directly observed in the current path |
| stack-frame resolution | favors nodes referenced by resolvable frames |
| historical failure overlap | favors nodes associated with highly similar prior failures |
| structural proximity | favors direct callers, callees, containers, and dependencies |
| graph criticality | provides a bounded tie-breaker for shared/high-connectivity infrastructure |

The implementation will ensure scores are normalized to `[0.0, 1.0]`. Confidence is derived from score and evidence quality; unresolved-only evidence cannot yield high confidence.

## Structured Output

Similarity results will include:

```json
{
  "targetNodeId": "function:...",
  "candidateNodeId": "function:...",
  "nodeType": "function",
  "score": 0.67,
  "featureScores": {"calledNodes": 0.50, "neighborNodes": 0.80},
  "matchedFeatures": {"calledNodes": ["function:..."]},
  "clusterId": "function-cluster:..."
}
```

Bug-localization results will include:

```json
{
  "resolvedFailurePath": {"nodeIds": ["function:..."], "unresolvedReferences": []},
  "similarPastFailures": [{"failureId": "incident-42", "similarity": 0.71}],
  "suspectedRootCauses": [
    {
      "nodeId": "function:...",
      "score": 0.84,
      "confidence": "high",
      "reasons": [{"kind": "on_failure_path", "weight": 0.40}]
    }
  ],
  "reasoningMetadata": {"historyRecordsCompared": 3}
}
```

## Public Service Interfaces

```python
class GraphFeatureExtractor:
    def build_index(self, graph: GraphPayload) -> GraphFeatureIndex: ...
    def features_for(self, node_id: str, profile: FeatureProfile) -> NodeFeatures: ...

class SimilarityEngine:
    def compare(self, target: NodeFeatures, candidate: NodeFeatures, profile: FeatureProfile) -> SimilarityResult: ...
    def rank_similar(self, graph: GraphPayload, target_node_id: str, limit: int, profile: FeatureProfile) -> SimilarityRanking: ...

class SimilarityClusterer:
    def cluster(self, graph: GraphPayload, node_type: str, threshold: float, profile: FeatureProfile) -> ClusterResult: ...

class FailurePathParser:
    def parse(self, graph: GraphPayload, failure: FailureInput) -> ResolvedFailurePath: ...

class BugLocalizer:
    def localize(self, graph: GraphPayload, failure: FailureInput, history: Sequence[HistoricalFailure], configuration: LocalizationConfiguration) -> BugLocalizationResult: ...
```

## Testing Strategy

- Unit-test Jaccard scores for overlap, disjoint sets, identical sets, and empty sets.
- Unit-test weighted aggregation, including disabled and unavailable feature families.
- Unit-test graph feature extraction using small hand-authored `GraphPayload` fixtures.
- Unit-test ranking for same-type filtering, self exclusion, deterministic ties, and result limits.
- Unit-test clustering for threshold boundaries, isolated nodes, and transitive components.
- Unit-test stack-trace resolution for known file-and-line frames, qualified names, ambiguity, and unresolved external frames.
- Unit-test localization with no history, matching history, confirmed historical causes, and weak/unresolved evidence.
- Contract-test that all Phase 6 services treat the Phase 3 graph payload as read-only.
- Keep fixtures repository-agnostic and independent of external services.

## Schema Changes

No Phase 3 graph-schema change is needed.

Phase 6 adds Pydantic models for similarity, clustering, failure input, history records, and localization results. Historical failure records are intentionally separate from the repository graph, preserving the existing graph schema and preventing unresolved external symbols from becoming graph nodes.

## Implementation Sequence

1. Add Phase 6 schemas and local failure-history fixture/store contract.
2. Implement graph indexing and feature extraction.
3. Implement weighted Jaccard comparison and ranking.
4. Implement threshold-based connected-component clustering.
5. Implement failure input normalization and resolution.
6. Implement history comparison and root-cause ranking.
7. Add fixtures, unit tests, and Phase 6 implementation README documentation.
