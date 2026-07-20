# GitHub Graph - Phase 9: Production Features

## Delivered capabilities

- User registration, login, and HMAC-signed bearer tokens.
- Repository ownership through a saved-repository catalog.
- Durable analysis history through snapshot listing.
- Snapshot-scoped JSON and PDF report downloads.
- Asynchronous ingestion with duplicate-scan reuse and retryable failed jobs.
- Clone byte/time limits plus Python analysis file-count and source-file-size bounds.
- Explicit operational failure categories, durable retry counts, and health-gated Docker startup.

## Authentication model

`POST /api/v1/auth/register` creates an account and returns an access token.
`POST /api/v1/auth/login` returns a new token. `GET /api/v1/auth/me` returns
the current identity. Deployed environments must set:

```bash
GITHUB_GRAPH_AUTH_ENABLED=true
GITHUB_GRAPH_AUTH_TOKEN_SECRET="long-random-secret"
```

When authentication is enabled, repository catalog, graph, intelligence,
explanation, history, export, and retry operations are scoped to repositories
saved by the authenticated user. Local Docker development defaults to a
temporary `local@github-graph.dev` workspace identity so first-run demos do not
need account setup.

## Repository operations

| API | Purpose |
| --- | --- |
| `GET /api/v1/repositories` | List saved repositories for the current user. |
| `GET /api/v1/repositories/{id}/snapshots` | List durable analysis snapshots. |
| `POST /api/v1/ingestion-jobs/{id}/retry` | Create a new job from a failed ingestion. |
| `GET /api/v1/repositories/{id}/exports/json` | Download the latest or selected snapshot as JSON. |
| `GET /api/v1/repositories/{id}/exports/pdf` | Download a concise PDF report. |

Submitting an already-completed repository URL returns the existing completed
job with `reused: true`; active jobs are also reused. A failed job is never
silently reused and must be retried explicitly.

## Large repository policy

The clone worker terminates clones exceeding configured time or byte limits.
The Python analysis worker rejects repositories above `ANALYSIS_MAX_FILES` and
does not parse individual Python files larger than
`ANALYSIS_MAX_SOURCE_FILE_BYTES`. These bounds protect worker memory and make
failure messages actionable rather than allowing an unbounded scan.

## Frontend

The landing page provides account registration/login and server-backed saved
repository history. The repository workspace provides a snapshot history tab,
JSON/PDF export controls, and a retry button when ingestion fails.

## Verification

Run the standard quality gate:

```bash
bash infra/run-stage4-e2e.sh
```

For production-auth smoke tests, start the stack with
`GITHUB_GRAPH_AUTH_ENABLED=true`, register an account, retain the returned
bearer token, then submit and retrieve a repository through the authenticated
API.
