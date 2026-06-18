# github-graph-analysis

FastAPI analysis service for GitHub Graph Phase 1.

## Responsibilities

- Accept analysis requests from the backend
- Read cloned repositories from shared workspace
- Extract file tree and manifest metadata
- Return normalized analysis payloads

## Local development

```bash
pip install .
uvicorn app.main:app --reload
```
