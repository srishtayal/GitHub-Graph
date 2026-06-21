# GitHub Graph - Phase 4

Phase 4 turns the extracted repository metadata into a queryable code graph.

## What this phase does

This phase converts the analysis output into a real dependency model that can be queried and visualized across the stack.

It introduces graph entities for:

- repositories
- files
- classes
- functions
- APIs
- modules

It introduces graph relationships for:

- `IMPORTS`
- `CALLS`
- `USES`
- `INHERITS`
- `BELONGS_TO`

The key outcome is a stable graph payload with deterministic node and edge IDs, duplicate suppression, and totals that can be displayed in the UI.

## Graph model

### Nodes

Each node is represented with:

- `id`: stable hashed identifier
- `type`: one of `repo`, `file`, `class`, `function`, `api`, `module`
- `label`: human-readable display label
- `properties`: type-specific metadata

### Edges

Each edge is represented with:

- `id`: stable hashed identifier
- `source`: source node id
- `target`: target node id
- `type`: one of `IMPORTS`, `CALLS`, `USES`, `INHERITS`, `BELONGS_TO`
- `properties`: relationship metadata

## JSON format

The analysis service now returns the full graph in the analysis response, and the backend stores that payload as JSON in the latest analysis result.

```json
{
  "summary": {
    "totalFiles": 18,
    "totalDirectories": 5,
    "languageSummary": {
      "Python": 18
    },
    "totalClasses": 6,
    "totalFunctions": 24,
    "totalMethodCalls": 41,
    "totalApiRoutes": 4,
    "totalModuleDependencies": 12,
    "totalGraphNodes": 31,
    "totalGraphEdges": 56
  },
  "graph": {
    "nodes": [
      {
        "id": "repo:3c4c8f4df80e7d8bbf6f",
        "type": "repo",
        "label": "requests",
        "properties": {
          "repositoryId": "repo-123",
          "githubUrl": "https://github.com/psf/requests"
        }
      },
      {
        "id": "file:2d1f0c7f1fdc3f4e1a88",
        "type": "file",
        "label": "requests/__init__.py",
        "properties": {
          "relativePath": "requests/__init__.py",
          "language": "Python"
        }
      }
    ],
    "edges": [
      {
        "id": "edge:9d2f1b5d2c0a8fb1",
        "source": "file:2d1f0c7f1fdc3f4e1a88",
        "target": "repo:3c4c8f4df80e7d8bbf6f",
        "type": "BELONGS_TO",
        "properties": {}
      }
    ]
  }
}
```

The analysis response also keeps the existing flattened arrays for files, classes, functions, imports, calls, inheritance, routes, and module dependencies so query consumers can use the representation that best fits their needs.

## Endpoints

### Analysis service

- `POST /internal/v1/analysis-jobs`

This endpoint runs extraction inside the FastAPI analysis service and returns the structured analysis payload, including the graph.

### Backend API

- `GET /api/v1/repositories/{repositoryId}/analysis`
- `GET /api/v1/repositories/{repositoryId}/graph`

The analysis endpoint returns the full stored analysis JSON.
The graph endpoint returns only the graph object for UI consumers and graph tooling.

## Storage path

The backend persists each completed analysis result as JSON in PostgreSQL. The stored payload includes:

- the extracted repository metadata
- the flattened structural arrays
- the full graph payload
- the graph totals in the summary

This keeps the graph queryable without requiring the UI to reconstruct it from partial data.

## UI behavior

The frontend now shows:

- graph node totals
- graph edge totals
- a breakdown of node types
- a short preview of graph nodes
- a short preview of graph relationships

## Limitations

- The graph is built from the extracted analysis model, so its quality depends on parser coverage.
- The Python parser is still the only language-aware static parser in this phase.
- The graph is returned and stored as JSON; there is not yet a separate Neo4j write path for these nodes and edges.
- Relationship resolution is intentionally conservative when the extractor cannot unambiguously resolve a symbol.

## Validation

Recommended validation sequence:

1. Rebuild the Docker stack with `docker compose build`.
2. Start the stack with `docker compose up -d` from `infra/`.
3. Submit a real public Python repository through the web UI or API.
4. Wait for the ingestion job to reach `COMPLETED`.
5. Confirm `/analysis` and `/graph` return non-empty payloads.
6. Confirm the frontend displays graph stats and a relationship preview.

## Example workflow

1. Create an ingestion job for a public Python repository.
2. The backend clones the repository and invokes the analysis service.
3. The analysis service extracts files, symbols, relationships, and graph data.
4. The backend stores the full JSON payload.
5. The UI fetches the latest analysis and graph data and displays the repository structure.
