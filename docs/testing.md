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

## Stage 2 real-service integration test

Run the isolated black-box integration suite from the repository root:

```bash
bash infra/run-stage2-integration.sh
```

The suite builds and starts a separate Docker Compose project containing the
real Spring Boot API, PostgreSQL, Neo4j, and Python analysis service. It:

- seeds two snapshots of one repository;
- creates and resolves a failure through the public API;
- confirms a graph node as the root cause;
- proves that the confirmed cause increases a later localization score;
- restarts the API and verifies that the failure still exists;
- proves that failure history is isolated by snapshot.

The test uses dedicated volumes and removes them when it finishes. Set
`KEEP_STAGE2_TEST_STACK=1` to retain the isolated stack for debugging.
