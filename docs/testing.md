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

## Stage 4 end-to-end quality gate

From the repository root:

```bash
bash infra/run-stage4-e2e.sh
```

This command:

1. Builds the Python `test` image target, which runs the complete Python suite.
2. Builds the Java `test` image target, which runs the complete Maven suite.
3. Builds the production Next.js image with `npm run build`.
4. Starts PostgreSQL, Neo4j, analysis, API, and web from empty isolated volumes.
5. Ingests `https://github.com/pallets/itsdangerous` by default.
6. Compares extracted graph IDs with persisted Neo4j graph IDs.
7. Calls all six Phase 5 analytics endpoints.
8. Verifies public similarity, stack localization, failure persistence, root
   cause confirmation, and later-score influence.
9. Runs the deterministic Stage 2 restart/snapshot-isolation integration suite.

Override the repository with `STAGE4_REPOSITORY_URL`. Retain the isolated stack
with `KEEP_STAGE4_TEST_STACK=1`.

Grounded AI checks are opt-in because they send bounded public-repository
evidence to Gemini:

```bash
STAGE4_INCLUDE_AI=1 bash infra/run-stage4-e2e.sh
```

The AI gate validates evidence IDs and source types, graph node/edge references,
snapshot metadata, model/prompt versions, and hypothesis language for root
causes.

## Phase 8 frontend contract verification

The frontend includes a deterministic local API covering every Phase 8
integration contract. Use it when the Docker backend is unavailable:

```bash
cd github-graph-web
npm run e2e:mock-api
```

In a second terminal:

```bash
cd github-graph-web
NEXT_PUBLIC_API_BASE_URL=http://127.0.0.1:8180 npm run dev -- --port 4173
```

Open `http://127.0.0.1:4173/repositories/phase8-demo`. The fixture supports
graph analytics, similarity, clustering, failure localization and persistence,
root-cause confirmation, and grounded explanation citations.
