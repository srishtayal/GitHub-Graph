# github-graph-analysis

FastAPI analysis service for GitHub Graph Phase 1.

## Responsibilities

- Accept analysis requests from the backend
- Read cloned repositories from shared workspace
- Extract file tree metadata
- Parse Python classes, functions, imports, calls, inheritance, API routes, and module dependencies
- Run graph analytics (DFS, BFS, components, topological sort, centrality, cycle detection)
- Return normalized analysis payloads

## Local development

```bash
pip install .
uvicorn app.main:app --reload
```

Run the extractor tests with:

```bash
PYTHONPATH=. python -m unittest discover -s tests -v
```

See [Phase 3](../PHASE_3.md) for the JSON contract and [Phase 5](../PHASE_5.md) for analytics endpoints.
