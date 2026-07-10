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
