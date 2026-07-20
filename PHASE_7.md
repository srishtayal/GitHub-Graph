# GitHub Graph - Phase 7

Phase 7 adds an orchestrated AI Explanation Layer. It turns existing structured
repository intelligence into concise natural-language answers while keeping all
claims bounded by supplied graph evidence.

## Scope

The explanation layer consumes:

- Phase 3/4 `GraphPayload` and repository metadata;
- Phase 5 DFS dependency traces, BFS impact analyses, centrality, cycles, and
  topological-sort results;
- Phase 6 similarity rankings/clusters and bug-localization results;
- optional existing symbol metadata and supplied snippets.

`GroundedQueryService` computes the required Phase 5/6 result from the loaded
graph. It does not scan raw repositories, alter graph schemas, or enable Gemini
tools that could provide outside information.

## Main service

`app.services.explanations.GroundedQueryService` is the public orchestration
entrypoint. Spring Boot loads the latest snapshot graph from Neo4j and
snapshot-scoped failure history from PostgreSQL, then calls
`POST /internal/v1/explanations/query`.

Public clients call:

```http
POST /api/v1/explanations/query
Content-Type: application/json

{
  "repositoryId": "<repository UUID>",
  "query": "What breaks if dbConnection fails?"
}
```

`targetNodeId`, `stackTrace`, and `errorLog` are optional. The original
`POST /internal/v1/explanations` endpoint remains available for trusted internal
callers that already have an `ExplanationRequest`.

1. `QueryRouter` deterministically selects an intent.
2. `TargetResolver` validates an explicit node or resolves a named graph node
   from the question.
3. `GroundedQueryService` runs the required DFS, BFS, centrality, similarity,
   localization, cycle, or topological analysis.
4. `EvidenceSelector` selects and bounds only the matching result and graph
   entities, assigning every prompt item a stable evidence ID.
5. `PromptBuilder` marks all user/repository text as untrusted, prohibits outside
   knowledge, and gives Gemini the allowed
   evidence, nodes, and edges.
6. `GeminiClient` requests JSON structured output using the official
   `google-genai` Python SDK with a timeout and bounded retries.
7. `ResponseParser` rejects unsupported evidence IDs, mismatched evidence source
   types, and unsupported node or edge references.

If an intent is unknown or its required output is absent, the service returns a
local `insufficient` response and does not call Gemini.

## Intent mapping

| Question category | Required input |
|---|---|
| Flow / dependency question | `DependencyTraceResult` |
| Impact / what-breaks question | `ImpactAnalysisResult` |
| Critical-function question | `CentralityResult` |
| Similarity question | `SimilarityRanking` and/or `ClusterResult` |
| Failure / root-cause question | `BugLocalizationResult` |
| Cycle/order question | `CycleDetectionResult` and/or `TopologicalSortResult` |
| Repository structure question | `GraphPayload` plus optional repository metadata |

## Grounding and confidence

Gemini is required to cite each substantive claim with a supplied evidence ID. It is
also told not to present a localization candidate as a certain root cause.
Response validation permits only node and edge IDs supplied in the selected
graph slice. Missing graph evidence returns a local `insufficient` response.
Missing model citations or unsupported references fail closed with HTTP 502.

## Configuration

Set the API key only through the process environment:

```bash
export GEMINI_API_KEY="..."
```

The default model is `gemini-3.1-flash-lite`; override it only through
`GEMINI_MODEL`. Provider and evidence controls are configured through:

- `GEMINI_TIMEOUT_SECONDS`
- `GEMINI_MAX_RETRIES`
- `GEMINI_RETRY_BACKOFF_SECONDS`
- `EXPLANATION_MAX_PROMPT_CHARS`
- `EXPLANATION_MAX_EVIDENCE_CHARS`
- `EXPLANATION_MAX_EVIDENCE_ITEMS`
- `EXPLANATION_MAX_REFERENCED_NODES`
- `EXPLANATION_MAX_REFERENCED_EDGES`

Keys and complete prompts are never logged. Responses record provider/model,
prompt version, orchestration version, repository ID, snapshot ID, branch, and
commit SHA.

## Tests

Unit tests use an injected fake transport. Adversarial fixtures cover prompt
injection, invented evidence, unsupported graph references, retries, and
insufficient evidence. A live provider smoke test should use a fully synthetic
graph so repository data is not exported merely to test provider connectivity.

## End-to-end verification

When `STAGE4_INCLUDE_AI=1`, `infra/e2e/stage4_e2e.py` asks:

- `Explain this dependency flow.`
- `What breaks if this function fails?`
- `Why is this error happening?`

The verifier rejects responses that have no supporting evidence, cite an
unsupported evidence ID/source pair, reference a node or edge outside the
persisted snapshot graph, omit snapshot/model versions, or present a bug cause
without hypothesis language.

AI mode is intentionally opt-in because it sends bounded public-repository graph
evidence to Gemini. The non-AI Stage 4 gates and synthetic provider smoke tests
do not require exporting an ingested repository graph.
