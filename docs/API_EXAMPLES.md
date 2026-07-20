# GitHub Graph API Examples

The importable Postman collection is at
`docs/postman/GitHub-Graph.postman_collection.json`. Set its collection
variables after ingestion:

- `repositoryId` and `jobId` come from the ingestion response.
- `snapshotId` comes from the repository summary.
- `functionNodeId` comes from a `function` node in the graph response.
- `failureId` comes from the stored-failure response.
- `rootCauseNodeId` comes from a localization candidate.

## Ingest and poll

```bash
curl -X POST http://localhost:8080/api/v1/repositories/ingestions \
  -H 'Content-Type: application/json' \
  -d '{"githubUrl":"https://github.com/pallets/itsdangerous"}'

curl http://localhost:8080/api/v1/ingestion-jobs/<jobId>
```

Wait for `status: "COMPLETED"`, then retrieve extraction and graph data:

```bash
curl http://localhost:8080/api/v1/repositories/<repositoryId>/analysis
curl http://localhost:8080/api/v1/repositories/<repositoryId>/graph
```

## Production authentication, history, and reports

Set `GITHUB_GRAPH_AUTH_ENABLED=true` and a strong
`GITHUB_GRAPH_AUTH_TOKEN_SECRET` before starting a deployed stack. Register or
log in, then pass the returned token on all repository requests:

```bash
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"user@example.com","password":"use-a-strong-password","displayName":"Example User"}'

curl http://localhost:8080/api/v1/repositories \
  -H 'Authorization: Bearer <accessToken>'

curl http://localhost:8080/api/v1/repositories/<repositoryId>/snapshots \
  -H 'Authorization: Bearer <accessToken>'

curl -L http://localhost:8080/api/v1/repositories/<repositoryId>/exports/json \
  -H 'Authorization: Bearer <accessToken>' \
  -o repository-report.json

curl -L "http://localhost:8080/api/v1/repositories/<repositoryId>/exports/pdf?snapshotId=<snapshotId>" \
  -H 'Authorization: Bearer <accessToken>' \
  -o repository-report.pdf
```

If a job fails, create a bounded asynchronous retry rather than re-submitting
the same URL:

```bash
curl -X POST http://localhost:8080/api/v1/ingestion-jobs/<jobId>/retry \
  -H 'Authorization: Bearer <accessToken>'
```

## Analytics and similarity

```bash
curl "http://localhost:8080/api/v1/analytics/path/<nodeId>?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/impact/<nodeId>?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/components?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/cycles?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/topological-order?repositoryId=<repositoryId>"
curl "http://localhost:8080/api/v1/analytics/critical?repositoryId=<repositoryId>&limit=20"
curl "http://localhost:8080/api/v1/intelligence/similarity/<nodeId>?repositoryId=<repositoryId>&limit=10"
```

## Grounded explanation

```bash
curl -X POST http://localhost:8080/api/v1/explanations/query \
  -H 'Content-Type: application/json' \
  -d '{
    "repositoryId": "<repositoryId>",
    "query": "What breaks if this function fails?",
    "targetNodeId": "<functionNodeId>"
  }'
```

Successful AI responses contain `supportingEvidence`, `referencedNodeIds`,
`referencedEdgeIds`, `snapshotMetadata`, and `modelMetadata`. Node and edge
references must exist in the selected snapshot graph.
