# GitHub Graph - Phase 7

Phase 7 adds a Python-only AI Explanation Layer. It turns existing structured
repository intelligence into concise natural-language answers while keeping all
claims bounded by supplied graph evidence.

## Scope

The layer consumes, but does not recompute or mutate:

- Phase 3/4 `GraphPayload` and repository metadata;
- Phase 5 DFS dependency traces, BFS impact analyses, centrality, cycles, and
  topological-sort results;
- Phase 6 similarity rankings/clusters and bug-localization results;
- optional existing symbol metadata and supplied snippets.

It does not scan raw repositories, add a FastAPI endpoint, alter graph schemas,
or enable Gemini tools that could provide outside information.

## Main service

`app.services.explanations.ExplanationService` accepts an
`ExplanationRequest` and returns an `ExplanationResponse`.

1. `QueryRouter` deterministically selects an intent.
2. `EvidenceSelector` selects only the matching precomputed result and graph
   entities, assigning every prompt item a stable evidence ID.
3. `PromptBuilder` prohibits outside knowledge and gives Gemini the allowed
   evidence, nodes, and edges.
4. `GeminiClient` requests JSON structured output using the official
   `google-genai` Python SDK.
5. `ResponseParser` validates the Pydantic response and removes unrecognized
   graph references. A response citing no supplied evidence is rejected.

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

Gemini is told to cite each substantive claim with a supplied evidence ID. It is
also told not to present a localization candidate as a certain root cause.
Response validation permits only node and edge IDs supplied in the selected
graph slice. Missing evidence, missing citations, or unsupported references
produce an `insufficient` outcome rather than an ungrounded answer.

## Configuration

Set the API key only through the process environment:

```bash
export GEMINI_API_KEY="..."
```

The default model is `gemini-3.1-flash-lite`; override it only through
`GEMINI_MODEL`. Keys and complete prompts are never logged by this layer.

## Tests

Phase 7 tests use an injected fake transport. No live Gemini credentials or
network calls are required to test routing, prompt construction, parsing, and
service behavior.
