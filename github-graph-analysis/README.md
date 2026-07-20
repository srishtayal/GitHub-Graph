# github-graph-analysis

FastAPI analysis service for GitHub Graph.

## Responsibilities

- Accept analysis requests from the backend
- Read cloned repositories from shared workspace
- Extract file tree metadata
- Parse Python classes, functions, imports, calls, inheritance, API routes, and module dependencies
- Run graph analytics (DFS, BFS, components, topological sort, centrality, cycle detection)
- Return normalized analysis payloads
- Analyze graph similarity, clusters, and failure-localization evidence
- Produce grounded Gemini explanations from precomputed graph-analysis evidence

## Local development

```bash
pip install .
uvicorn app.main:app --reload
```

Run the complete Python suite with:

```bash
PYTHONPATH=. python -m unittest discover -s tests -v
```

See [Phase 3](../PHASE_3.md) for the JSON contract, [Phase 4](../PHASE_4.md) for graph construction, and [Phase 5](../PHASE_5.md) for analytics endpoints and design.

To run only the Phase 6 tests:

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

See [Phase 6](../PHASE_6.md) for the implementation details and [Phase 6 solutioning](../PHASE_6_SOLUTIONING.md) for the design record.

## Phase 7: AI Explanation Layer

Phase 7 accepts an `ExplanationRequest` with a user question, `GraphPayload`,
and whichever precomputed Phase 5/6 result applies. It never scans a repository
or asks Gemini to infer facts beyond the supplied evidence.

Configure Gemini outside source control:

```bash
export GEMINI_API_KEY="..."
# Optional; the default is the lightest Flash model.
export GEMINI_MODEL="gemini-3.1-flash-lite"
```

Run the focused tests:

```bash
PYTHONPATH=. python -m unittest \
  tests.test_phase7_schemas \
  tests.test_query_router \
  tests.test_evidence_selector \
  tests.test_prompt_builder \
  tests.test_response_parser \
  tests.test_gemini_client \
  tests.test_explanation_service -v
```

See [Phase 7](../PHASE_7.md) for the evidence contract and grounded-response behavior.

### Postman endpoint

When the analysis service is running, submit precomputed graph evidence to:

```text
POST http://localhost:8000/internal/v1/explanations
Content-Type: application/json
```

The response is an `ExplanationResponse`. A question whose required analysis
result is missing returns an `insufficient` response without contacting Gemini.
Missing Gemini configuration returns HTTP 503; provider or invalid-model
responses return HTTP 502.
