# github-graph-analysis

FastAPI analysis service for GitHub Graph.

## Responsibilities

- Accept analysis requests from the backend
- Read cloned repositories from shared workspace
- Extract file tree metadata
- Parse Python classes, functions, imports, calls, inheritance, API routes, and module dependencies
- Return normalized analysis payloads
- Analyze graph similarity, clusters, and failure-localization evidence

## Local development

```bash
pip install .
uvicorn app.main:app --reload
```

Run the complete Python suite with:

```bash
PYTHONPATH=. python -m unittest discover -s tests -v
```

See [Phase 3](../PHASE_3.md) for the JSON contract, [Phase 4](../PHASE_4.md) for graph construction, and [Phase 5](../PHASE_5.md) for the approved Python analytics design.

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
