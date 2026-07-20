# GitHub Graph - Phase 6

Phase 6 adds the **Similarity and Bug-Localization Engine** to the Python analysis service. It consumes the existing Phase 3 `GraphPayload`, returns JSON-ready Pydantic models, and is integrated through internal Python and public Spring Boot APIs.

For the approved design, see [PHASE_6_SOLUTIONING.md](PHASE_6_SOLUTIONING.md).

## Capabilities

- Weighted Jaccard similarity for functions, files, modules, and failure paths.
- Same-type similarity ranking with deterministic ties.
- Threshold-linked transitive clusters using connected components.
- Failure-path resolution from node IDs, Python stack frames, and error logs.
- PostgreSQL-backed, repository-and-snapshot-scoped failure history.
- Explainable root-cause ranking with confidence values.

## Graph Compatibility

Phase 6 reads the current graph without modifying it:

- Nodes: `repo`, `file`, `class`, `function`, `api`, `module`
- Edges: `BELONGS_TO`, `IMPORTS`, `CALLS`, `USES`, `INHERITS`

Unresolved IDs and external stack frames remain outside the graph and appear in `unresolvedReferences`.

## Services

| Service | Responsibility |
|---|---|
| `GraphFeatureExtractor` | Builds read-only namespaced feature sets. |
| `SimilarityEngine` | Computes weighted Jaccard scores and rankings. |
| `SimilarityClusterer` | Groups threshold-qualified similarity links transitively. |
| `FailurePathParser` | Resolves failure evidence and records unresolved references. |
| `JsonFailureHistoryStore` | Reads deterministic JSON fixtures in tests only. |
| `FailureHistoryService` | Persists runtime failure history in PostgreSQL. |
| `BugLocalizer` | Discovers impacts, compares history, and ranks causes. |

## Default Similarity Weights

| Subject | Weights |
|---|---|
| Function | calls 0.40, neighbors 0.25, callers 0.20, enclosing imports 0.15 |
| File | imports 0.40, internal dependencies 0.30, symbols 0.20, neighbors 0.10 |
| Module | importing files 0.45, using files 0.35, neighbors 0.20 |
| Failure path | path nodes 0.45, files 0.20, dependencies 0.20, error signature 0.15 |

An aggregate score is the normalized weighted mean of enabled feature families. Empty-set comparisons score `0.0`.

## Root-Cause Ranking

The localizer traverses from resolved failure nodes up to a configured depth. Candidates receive explainable evidence from current path membership, resolved stack frames, confirmed causes of similar past failures, structural proximity, and local graph degree. High confidence requires direct path or stack evidence; unresolved evidence alone cannot produce it.

Runtime historical failures come from PostgreSQL and are filtered by repository
and snapshot before being sent to Python. JSON fixtures containing `failureId`,
`repositoryId`, `occurredAt`, `failurePathNodeIds`, `errorSignature`, optional
`confirmedRootCauseNodeIds`, and `metadata` remain available for unit tests.

## HTTP Integration

Internal Python endpoints:

- `POST /internal/v1/intelligence/similarity`
- `POST /internal/v1/intelligence/clusters`
- `POST /internal/v1/intelligence/localize`

Public Spring Boot endpoints:

- `GET /api/v1/intelligence/similarity/{nodeId}?repositoryId=...`
- `GET /api/v1/intelligence/clusters?repositoryId=...`
- `POST /api/v1/intelligence/failures/localize`
- `POST /api/v1/repositories/{repositoryId}/failures`
- `GET /api/v1/repositories/{repositoryId}/failures`
- `PATCH /api/v1/failures/{failureId}`

All graph payloads are loaded by Spring Boot from Neo4j. Callers can optionally
provide `snapshotId`; otherwise the latest snapshot is resolved.

## Testing

From `github-graph-analysis/`, after installing dependencies:

```bash
PYTHONPATH=. python -m unittest \
  tests.test_phase6_schemas \
  tests.test_failure_history_store \
  tests.test_graph_feature_extractor \
  tests.test_similarity_engine \
  tests.test_similarity_clustering \
  tests.test_failure_path_parser \
  tests.test_bug_localizer -v
```
