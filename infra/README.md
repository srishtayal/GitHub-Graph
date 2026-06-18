# infra

Docker-based local development setup for GitHub Graph.

## Services

- `web` - Next.js frontend
- `api` - Spring Boot backend
- `analysis` - FastAPI analysis engine
- `postgres` - relational metadata store
- `neo4j` - graph store

## Start everything

```bash
docker compose up --build
```

Run this command from the `infra/` directory.
