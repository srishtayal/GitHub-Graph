# Testing

## Backend tests

Run from:

`github-graph-api/`

```bash
mvn test
```

This covers:

- traversal algorithms
- cycle detection
- topological sort
- centrality
- analytics controller responses

## Python tests

Run from:

`github-graph-analysis/`

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -e .
PYTHONPATH=. python -m unittest discover -s tests
```

## Phase 6 focused tests

Run from `github-graph-analysis/` after installing the Python dependencies:

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

## Docker testing

From:

`infra/`

```bash
docker compose build api analysis web
docker compose up -d
docker compose ps
```

Then call:

```bash
curl "http://localhost:8080/api/v1/analytics/cycles?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/topological-order?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/critical?repositoryId=<repositoryId>&limit=10"
curl "http://localhost:8080/api/v1/analytics/impact/<nodeId>?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/path/<nodeId>?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/components?repositoryId=<repositoryId>"
```
