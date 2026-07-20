# GitHub Graph

GitHub Graph ingests a public Python repository, extracts structured code data,
persists a dependency graph, runs graph analytics and similarity/localization,
and produces evidence-grounded explanations.

## Quick start

```bash
cd infra
cp .env.example .env
docker compose up --build
```

Local services:

- Frontend: `http://localhost:3000`
- Spring Boot API: `http://localhost:8080`
- Analysis service: `http://localhost:8000`
- Neo4j Browser: `http://localhost:7474`

See [infra/README.md](infra/README.md) for configuration and startup details.

## Implemented scope

- Phase 2: validated public-repository ingestion, bounded cloning, snapshots,
  relational indexing, and persistent clone storage.
- Phase 3: Python files, classes, functions, imports, calls, inheritance, API
  routes, and module dependencies.
- Phase 4: stable repository/file/class/function/API/module graph persisted in
  Neo4j.
- Phase 5: DFS, BFS, components, topological ordering, centrality, and cycle
  detection through public APIs.
- Phase 6: weighted similarity, clustering, stack-trace localization, durable
  failure history, confirmed root causes, and feedback into later ranking.
- Phase 7: automatic grounded-query orchestration, bounded Gemini evidence,
  strict citations, prompt-injection defenses, and snapshot/model metadata.
- Stage 4: repeatable clean-stack end-to-end and quality-gate automation.

Deep static extraction is currently Python-only. Java, JavaScript, and
TypeScript deep parsers remain roadmap work.

## Verification

Run the isolated Stage 4 gate from the repository root:

```bash
bash infra/run-stage4-e2e.sh
```

It executes Python and Java tests during image builds, performs a production
Next.js build, starts a clean isolated stack, ingests a real public Python
repository, verifies Phases 2-6, and runs the existing persistence integration
suite.

AI verification is opt-in because it transfers bounded public-repository graph
evidence to Gemini:

```bash
STAGE4_INCLUDE_AI=1 bash infra/run-stage4-e2e.sh
```

API examples are in [docs/API_EXAMPLES.md](docs/API_EXAMPLES.md), with an
importable [Postman collection](docs/postman/GitHub-Graph.postman_collection.json).

## Documentation

- [Phase 2 ingestion](PHASE_2.md)
- [Phase 3 extraction](PHASE_3.md)
- [Phase 4 graph](PHASE_4.md)
- [Phase 5 analytics](PHASE_5.md)
- [Phase 6 intelligence](PHASE_6.md)
- [Phase 7 explanations](PHASE_7.md)
- [Stage 4 verification](STAGE_4_VERIFICATION.md)
- [End-to-end project plan](PROJECT_END_TO_END_PLAN.md)
- [Testing guide](docs/testing.md)
