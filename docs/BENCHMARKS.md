# Benchmark Snapshot

This page records measured project evidence, not synthetic performance claims.
Timing values are local Docker measurements from 2026-07-20 and will vary by
machine, Docker allocation, network state, and repository size.

## Quality gates

| Check | Result |
| --- | --- |
| Python analysis tests | 88 passed |
| Spring Boot tests | 34 passed |
| Next.js production build | Passed |
| Docker service health | PostgreSQL, Neo4j, Python analysis, Spring Boot API, and Next.js web service healthy |

## Real public-repository run

The isolated Stage 4 gate analyzed
[`pallets/itsdangerous`](https://github.com/pallets/itsdangerous) from a clean
stack.

| Measurement | Result |
| --- | --- |
| Persisted graph nodes | 349 |
| Persisted graph edges | 798 |
| Phase 5 public analytics endpoints | 6 of 6 passed |
| Phase 6 similarity endpoint | Passed |
| Root-cause localization and confirmed-cause feedback | Passed |

The full reproducible record is in [Stage 4 verification](../STAGE_4_VERIFICATION.md).

## Local API timing snapshot

These single-request timings used the healthy local Docker stack and an already
persisted `pallets/itsdangerous` snapshot. They are useful as demo reference
points, not service-level objectives.

| Endpoint | HTTP | Response size | Time |
| --- | ---: | ---: | ---: |
| `GET /api/v1/repositories` | 200 | 977 B | 72 ms |
| `GET /api/v1/repositories/{id}/graph` | 200 | 238,811 B | 210 ms |
| `GET /api/v1/repositories/{id}/exports/json` | 200 | 340,713 B | 108 ms |
| `GET /api/v1/repositories/{id}/exports/pdf` | 200 | 1,098 B | Verified successfully |

## Reproduce timing

Start the stack, ingest a public repository, then replace `<repositoryId>`:

```bash
curl --output /dev/null --write-out '%{http_code} %{time_total} %{size_download}\n' \
  http://localhost:8080/api/v1/repositories/<repositoryId>/graph
```

For placement conversations, explain that graph size and clone/network time
dominate cold analysis. Repeated submissions reuse completed snapshots instead
of starting a duplicate scan.
