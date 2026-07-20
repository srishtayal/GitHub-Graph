# infra

Docker-based local development setup for GitHub Graph.

## Services

- `web` - Next.js frontend
- `api` - Spring Boot backend
- `analysis` - FastAPI analysis engine
- `postgres` - relational metadata store
- `neo4j` - graph store

## Start everything

Create your local environment file once:

```bash
cp .env.example .env
```

The checked-in defaults are for local development only. `GITHUB_TOKEN` is
optional, but adding a token increases GitHub API rate limits. `GEMINI_API_KEY`
is optional until Phase 7 explanations are used. Never commit `.env`.

Start the stack:

```bash
docker compose up --build
```

Run this command from the `infra/` directory.

Compose waits for PostgreSQL, Neo4j, and the analysis service to become healthy
before starting the API. The web service starts after the API health endpoint
is ready.

## Local URLs

- Frontend: `http://localhost:3000`
- Backend health: `http://localhost:8080/actuator/health`
- Analysis health: `http://localhost:8000/internal/v1/health`
- Neo4j Browser: `http://localhost:7474`

## Repository limits

The default clone timeout is 120 seconds and the default maximum retained clone
size is 250 MiB. Override `REPOSITORY_CLONE_TIMEOUT_SECONDS` and
`REPOSITORY_CLONE_MAX_SIZE_BYTES` in `.env` when needed.

PostgreSQL, Neo4j, and cloned repository snapshots use named Docker volumes, so
ordinary container rebuilds and restarts preserve existing analyses.

## Clean end-to-end verification

From the repository root:

```bash
bash infra/run-stage4-e2e.sh
```

The script uses the separate `github-graph-stage4-e2e` Compose project and empty
volumes, so it does not modify the normal development stack. It builds all
quality gates, starts services with health dependencies, ingests a real public
Python repository, and removes the isolated resources afterward.

Set `STAGE4_INCLUDE_AI=1` only when external transfer of bounded public-repository
evidence to Gemini is approved.
