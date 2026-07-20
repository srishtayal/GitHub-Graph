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
