# Stage 4 End-to-End Verification

## Purpose

Stage 4 verifies the implemented Phase 2 through Phase 7 workflows against real
services rather than mocks. The gate uses a separate Docker Compose project and
empty PostgreSQL, Neo4j, and repository-cache volumes.

Run it from the repository root:

```bash
bash infra/run-stage4-e2e.sh
```

The default public repository is `https://github.com/pallets/itsdangerous`.
Override it with `STAGE4_REPOSITORY_URL`.

## Automated coverage

The Stage 4 runner:

1. Runs the complete Python test suite while building the analysis test target.
2. Runs the complete Maven test suite while building the API test target.
3. Runs the production Next.js build and starts the production web server.
4. Starts all services from empty isolated volumes and waits for health checks.
5. Ingests a real public Python repository and waits for completion.
6. Retrieves the relational index, structured extraction, and Neo4j graph.
7. Proves that extracted and persisted graph node IDs match.
8. Calls DFS, BFS, connected-components, cycle, topological-sort, and
   centrality endpoints.
9. Finds similar functions through the public Phase 6 API.
10. Submits a real graph-derived stack frame and receives root-cause candidates.
11. Confirms a root cause and proves that failure history increases its later
    localization score.
12. Runs the Stage 2 restart and snapshot-isolation persistence suite after the
    Stage 4 stack has been removed.

The verifier selects function and edge IDs from the ingested graph. It does not
depend on hard-coded graph fixtures.

## Grounded AI checks

The three Phase 7 questions are implemented in the gate:

- `Explain this dependency flow.`
- `What breaks if this function fails?`
- `Why is this error happening?`

Enable them only when sending bounded public-repository evidence to Gemini is
approved:

```bash
STAGE4_INCLUDE_AI=1 bash infra/run-stage4-e2e.sh
```

For every response, the gate validates the routed intent, evidence citations,
referenced node and edge IDs, repository and snapshot metadata, model and prompt
versions, and uncertainty language for root-cause hypotheses.

## Verification record

Verification on 2026-07-20 produced these results:

| Gate | Result |
| --- | --- |
| Python tests | Passed, 77 tests |
| Java tests | Passed, 30 tests |
| Next.js production build | Passed |
| Frontend dependency audit | Passed, 0 vulnerabilities |
| Production web health check | Passed |
| Public repository ingestion | Passed |
| Extraction and Neo4j graph retrieval | Passed, 349 nodes and 798 edges |
| All six Phase 5 public endpoints | Passed |
| Public Phase 6 similarity | Passed |
| Localization and root-cause candidates | Passed |
| Confirmed-cause feedback influence | Passed |
| Real-repository Gemini questions | Not run without external-transfer approval |
| Independent Stage 2 restart rerun | Pending Docker Desktop recovery |

The primary black-box run used repository
`https://github.com/pallets/itsdangerous`, repository ID
`f7cd9c0e-0dbd-4a4c-8b1e-bb64f08c22a9`, and snapshot ID
`ac28d45c-0566-4536-8a72-5f75e161fc7b`.

Docker Desktop exhausted its host disk while a second Neo4j stack was being
started for the independent persistence suite. The runner now removes the
Stage 4 stack before starting Stage 2, preventing both Neo4j stacks from running
at once. The remaining persistence rerun requires Docker Desktop to be restarted.

## API examples

Use [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md) for curl examples or import
[docs/postman/GitHub-Graph.postman_collection.json](docs/postman/GitHub-Graph.postman_collection.json)
into Postman.
