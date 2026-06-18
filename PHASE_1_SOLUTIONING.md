# GitHub Graph - Phase 1 Solutioning

This document captures the proposed Phase 1 system design for **GitHub Graph** before implementation begins.

## Scope

Phase 1 focuses on the system foundation:

1. Separate repo structure for frontend, backend, and analysis engine
2. Clean architecture for each service
3. Repository ingestion flow
4. GitHub URL submission endpoint
5. Clone public repos
6. Basic metadata extraction
7. PostgreSQL schema
8. Neo4j schema planning for graph storage
9. REST API contracts between backend and Python service
10. Docker setup for local development
11. Minimal README and setup instructions

## Constraints

- Frontend: Next.js
- Backend: Spring Boot
- Analysis engine: Python
- Communication: REST APIs
- Database: PostgreSQL + Neo4j
- Auth: none for now
- Repo support: public GitHub repositories only
- Deployment: local development + Docker
- Design goal: production-oriented and easy to extend later

## 1. Overall Architecture

Use a 3-service system plus databases:

1. **Frontend (`github-graph-web`)**
   - Next.js app
   - Lets users submit a GitHub repo URL
   - Shows ingestion status and extracted metadata

2. **Backend (`github-graph-api`)**
   - Spring Boot
   - Main system entrypoint for the frontend
   - Validates requests
   - Creates ingestion records
   - Orchestrates clone/analyze/store flow
   - Persists relational state in PostgreSQL
   - Exposes API for status and results

3. **Analysis Engine (`github-graph-analysis`)**
   - Python service
   - Accepts analysis requests from the backend
   - Reads cloned repository contents
   - Extracts normalized metadata
   - Can evolve later into deeper language-aware parsing

4. **Datastores**
   - **PostgreSQL** for operational and metadata storage
   - **Neo4j** for graph-based repository structure and relationships

### Recommended service boundary

- Frontend: UI only
- Backend: orchestration, validation, persistence, API ownership
- Analysis engine: source scanning and structural extraction only
- Neo4j: graph data only, not workflow state

### Recommended Phase 1 execution model

Use backend-managed asynchronous jobs without introducing a message broker yet:

- `POST /api/v1/repositories/ingestions` creates a job
- backend processes ingestion in the background
- frontend polls `GET /api/v1/ingestion-jobs/{jobId}`

This keeps the initial design simple while remaining extendable.

## 2. Responsibilities of Each Repo

### `github-graph-web`

- Submit GitHub repository URL
- Display ingestion job status
- Show repository metadata summary
- Show basic extracted file/module stats
- Keep business logic minimal

### `github-graph-api`

- REST API for frontend
- GitHub URL validation
- Public repo restriction enforcement
- Clone orchestration
- Job lifecycle management
- PostgreSQL persistence
- Analysis engine integration
- Neo4j write coordination or graph payload staging
- Docker/dev composition ownership

### `github-graph-analysis`

- Receive analysis request from backend
- Read cloned repository contents
- Extract:
  - repo root structure
  - file inventory
  - language/file type counts
  - package/build manifest discovery
  - basic import/reference extraction if feasible later
- Return normalized analysis JSON
- Stay stateless

## 3. Folder Structure for Each Repo

### Frontend repo: `github-graph-web`

```text
github-graph-web/
├── src/
│   ├── app/
│   │   ├── page.tsx
│   │   ├── repositories/
│   │   │   └── [jobId]/page.tsx
│   │   └── layout.tsx
│   ├── components/
│   │   ├── repo-url-form/
│   │   ├── ingestion-status/
│   │   └── metadata-summary/
│   ├── lib/
│   │   ├── api-client.ts
│   │   ├── types.ts
│   │   └── env.ts
│   └── styles/
├── public/
├── package.json
├── next.config.js
├── tsconfig.json
├── Dockerfile
├── .env.example
└── README.md
```

Notes:

- Use App Router
- Keep the UI minimal for Phase 1
- Keep API client logic separate from components

### Backend repo: `github-graph-api`

```text
github-graph-api/
├── src/
│   ├── main/
│   │   ├── java/com/githubgraph/api/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── domain/
│   │   │   │   ├── repository/
│   │   │   │   ├── ingestion/
│   │   │   │   └── analysis/
│   │   │   ├── service/
│   │   │   ├── persistence/
│   │   │   │   ├── entity/
│   │   │   │   ├── repository/
│   │   │   │   └── mapper/
│   │   │   ├── client/
│   │   │   │   ├── github/
│   │   │   │   ├── analysis/
│   │   │   │   └── neo4j/
│   │   │   ├── job/
│   │   │   ├── exception/
│   │   │   └── GithubGraphApiApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   └── test/
├── build.gradle or pom.xml
├── Dockerfile
├── .env.example
└── README.md
```

Notes:

- `controller`: HTTP entrypoints
- `domain`: business models and use cases
- `service`: orchestration logic
- `client`: outbound integrations
- `job`: async ingestion execution

### Analysis repo: `github-graph-analysis`

```text
github-graph-analysis/
├── app/
│   ├── api/
│   │   └── routes/
│   ├── core/
│   │   ├── config.py
│   │   ├── logging.py
│   │   └── exceptions.py
│   ├── schemas/
│   │   ├── requests.py
│   │   └── responses.py
│   ├── services/
│   │   ├── repo_scanner.py
│   │   ├── metadata_extractor.py
│   │   ├── manifest_detector.py
│   │   └── graph_planner.py
│   ├── models/
│   └── main.py
├── tests/
├── requirements.txt or pyproject.toml
├── Dockerfile
├── .env.example
└── README.md
```

Notes:

- Recommended framework: **FastAPI**

## 4. PostgreSQL Design

Use PostgreSQL for operational state and extracted metadata summaries.

### `repositories`

Represents a logical GitHub repository.

```text
id                    uuid pk
github_url            text unique not null
owner                 varchar not null
name                  varchar not null
default_branch        varchar null
is_public             boolean not null
status                varchar not null
created_at            timestamp not null
updated_at            timestamp not null
last_ingested_at      timestamp null
```

### `ingestion_jobs`

Tracks every ingestion attempt.

```text
id                    uuid pk
repository_id         uuid fk -> repositories.id
status                varchar not null
submitted_url         text not null
clone_path            text null
error_message         text null
started_at            timestamp null
finished_at           timestamp null
created_at            timestamp not null
updated_at            timestamp not null
```

Recommended statuses:

- `PENDING`
- `CLONING`
- `ANALYZING`
- `STORING`
- `COMPLETED`
- `FAILED`

### `repository_snapshots`

Represents a specific analyzed state of a repository.

```text
id                    uuid pk
repository_id         uuid fk
ingestion_job_id      uuid fk
branch_name           varchar null
commit_sha            varchar null
commit_message        text null
commit_author         varchar null
committed_at          timestamp null
root_directory        text not null
total_files           integer not null
total_directories     integer not null
language_summary_json jsonb not null
created_at            timestamp not null
```

### `files`

Stores file-level metadata for a snapshot.

```text
id                    uuid pk
snapshot_id           uuid fk -> repository_snapshots.id
relative_path         text not null
file_name             varchar not null
extension             varchar null
language              varchar null
size_bytes            bigint not null
is_binary             boolean not null
checksum              varchar null
created_at            timestamp not null
```

### `detected_manifests`

Stores discovered build/package/config manifests.

```text
id                    uuid pk
snapshot_id           uuid fk
file_id               uuid fk -> files.id
manifest_type         varchar not null
relative_path         text not null
metadata_json         jsonb not null
created_at            timestamp not null
```

### `analysis_results`

Stores raw and normalized Python output for traceability.

```text
id                    uuid pk
ingestion_job_id      uuid fk
snapshot_id           uuid fk
result_version        varchar not null
payload_json          jsonb not null
created_at            timestamp not null
```

### Why this works

- separates repository identity, job execution, and analyzed snapshot state
- supports re-ingestion later
- supports multiple analyses over time
- preserves raw analysis payloads for debugging and migration

## 5. Neo4j Graph Model

For Phase 1, define the model even if the first implementation is modest.

### Node labels

#### `Repository`

Properties:

- `repositoryId`
- `githubUrl`
- `owner`
- `name`

#### `Snapshot`

Properties:

- `snapshotId`
- `commitSha`
- `branchName`
- `createdAt`

#### `Directory`

Properties:

- `path`
- `name`

#### `File`

Properties:

- `fileId`
- `path`
- `name`
- `extension`
- `language`

#### `Manifest`

Properties:

- `manifestType`
- `path`

### Optional Phase 1.5 or later

- `Symbol`
- `Module`
- `Class`
- `Function`

### Relationships

- `(Repository)-[:HAS_SNAPSHOT]->(Snapshot)`
- `(Snapshot)-[:HAS_DIRECTORY]->(Directory)`
- `(Snapshot)-[:HAS_FILE]->(File)`
- `(Directory)-[:CONTAINS]->(Directory)`
- `(Directory)-[:CONTAINS]->(File)`
- `(Snapshot)-[:HAS_MANIFEST]->(Manifest)`
- `(Manifest)-[:DESCRIBES]->(File)` if needed

Later:

- `(File)-[:IMPORTS]->(File|Module)`
- `(Class)-[:DEPENDS_ON]->(Class)`
- `(Function)-[:CALLS]->(Function)`

### Recommended Phase 1 graph scope

- store file tree + manifests now
- defer deeper semantic relationships to later phases
- reuse stable IDs from PostgreSQL where practical

## 6. REST API Design

### Frontend -> Backend

#### Submit repository

`POST /api/v1/repositories/ingestions`

Request:

```json
{
  "githubUrl": "https://github.com/owner/repo"
}
```

Response:

```json
{
  "jobId": "uuid",
  "repositoryId": "uuid",
  "status": "PENDING"
}
```

#### Get ingestion job

`GET /api/v1/ingestion-jobs/{jobId}`

Response:

```json
{
  "jobId": "uuid",
  "repositoryId": "uuid",
  "status": "ANALYZING",
  "errorMessage": null,
  "createdAt": "timestamp",
  "startedAt": "timestamp",
  "finishedAt": null
}
```

#### Get repository summary

`GET /api/v1/repositories/{repositoryId}`

Response:

```json
{
  "repositoryId": "uuid",
  "githubUrl": "https://github.com/owner/repo",
  "owner": "owner",
  "name": "repo",
  "status": "COMPLETED",
  "latestSnapshot": {
    "snapshotId": "uuid",
    "commitSha": "abc123",
    "totalFiles": 120,
    "totalDirectories": 18,
    "languageSummary": {
      "Java": 40,
      "TypeScript": 20
    }
  }
}
```

#### Get repository files

`GET /api/v1/repositories/{repositoryId}/files`

Optional query params:

- `snapshotId`
- `pathPrefix`
- `language`

### Backend -> Analysis Engine

#### Analyze cloned repository

`POST /internal/v1/analysis-jobs`

Request:

```json
{
  "ingestionJobId": "uuid",
  "repositoryId": "uuid",
  "localPath": "/workspace/repos/owner_repo_xxx",
  "githubUrl": "https://github.com/owner/repo"
}
```

Response:

```json
{
  "ingestionJobId": "uuid",
  "status": "COMPLETED",
  "snapshot": {
    "branchName": "main",
    "commitSha": "abc123"
  },
  "summary": {
    "totalFiles": 120,
    "totalDirectories": 18,
    "languageSummary": {
      "Java": 40,
      "Python": 12
    }
  },
  "files": [
    {
      "relativePath": "src/main/App.java",
      "fileName": "App.java",
      "extension": ".java",
      "language": "Java",
      "sizeBytes": 2100,
      "isBinary": false
    }
  ],
  "manifests": [
    {
      "manifestType": "maven-pom",
      "relativePath": "pom.xml",
      "metadata": {}
    }
  ],
  "graph": {
    "nodes": [],
    "edges": []
  }
}
```

#### Health

`GET /internal/v1/health`

## 7. Ingestion Flow Sequence

Recommended Phase 1 sequence:

1. User enters GitHub URL in frontend
2. Frontend calls backend `POST /api/v1/repositories/ingestions`
3. Backend validates:
   - valid GitHub URL format
   - public GitHub URL only
   - no authentication required
4. Backend creates `repository` if new
5. Backend creates `ingestion_job` with `PENDING`
6. Background job starts
7. Backend sets status `CLONING`
8. Backend clones repo into a controlled workspace directory
9. Backend reads git metadata:
   - default branch/current branch
   - HEAD commit SHA
10. Backend sets status `ANALYZING`
11. Backend calls Python analysis service with local path
12. Python scans repo and returns normalized metadata
13. Backend sets status `STORING`
14. Backend writes snapshot/files/manifests/raw payload to PostgreSQL
15. Backend writes file tree graph to Neo4j
16. Backend marks job `COMPLETED`
17. Frontend polls and renders the result

Failure path:

- any failure updates job to `FAILED`
- preserve error message
- keep PostgreSQL writes transactional where possible

## 8. Docker Setup Approach

Use a multi-repo parent setup for local orchestration.

### Recommended workspace layout

```text
github-graph/
├── github-graph-web/
├── github-graph-api/
├── github-graph-analysis/
└── infra/
    ├── docker-compose.yml
    ├── .env
    └── README.md
```

If desired, `infra` can later become its own repo. For Phase 1, a shared parent workspace keeps local setup simpler.

### Docker Compose services

- `web`
- `api`
- `analysis`
- `postgres`
- `neo4j`

### Volumes

- persistent PostgreSQL volume
- persistent Neo4j volume
- shared clone workspace volume between `api` and `analysis`

The shared clone volume matters if backend owns cloning and Python reads local files.

### Networking

- internal Docker network
- API reachable by web
- analysis reachable by API
- databases reachable by API
- Neo4j optionally exposed locally for browser access

### Local development modes

Support both:

1. **Full Docker**
   - all services run in containers

2. **Hybrid local dev**
   - frontend/backend/analysis run locally
   - Postgres + Neo4j run via Docker

Hybrid mode will likely be faster for day-to-day Phase 1 development.

## 9. Exact Implementation Order for Phase 1

1. **Create multi-repo workspace structure**
   - web
   - api
   - analysis
   - infra

2. **Initialize each repo skeleton**
   - Next.js app
   - Spring Boot app
   - Python REST service
   - Dockerfiles
   - README stubs

3. **Define shared contracts first**
   - ingestion statuses
   - backend API request/response DTOs
   - backend-analysis REST payloads
   - error response format

4. **Set up PostgreSQL schema**
   - migrations for repositories, jobs, snapshots, files, manifests, analysis_results

5. **Set up backend architecture**
   - controller
   - service
   - persistence
   - background ingestion executor
   - health endpoints

6. **Implement GitHub URL validation**
   - accept public GitHub repo URLs only
   - normalize owner/repo

7. **Implement clone flow in backend**
   - local clone directory management
   - git metadata extraction
   - error handling

8. **Implement analysis service contract**
   - health endpoint
   - analysis endpoint
   - request/response schemas

9. **Implement basic Python metadata extraction**
   - walk repo tree
   - classify files
   - detect manifests
   - produce summary output

10. **Integrate backend -> analysis service**
    - REST client
    - store returned results in PostgreSQL

11. **Implement initial Neo4j write path**
    - repository/snapshot/directory/file nodes
    - containment relationships

12. **Implement frontend**
    - repo URL submission form
    - job status polling page
    - repository summary page

13. **Add Docker Compose setup**
    - service wiring
    - env config
    - shared volume for cloned repos

14. **Write minimal READMEs**
    - local setup
    - Docker setup
    - service responsibilities
    - API overview

15. **Smoke test end-to-end**
    - submit a public repo
    - clone
    - analyze
    - persist
    - display result

## 10. Decisions to Confirm Before Implementation

Recommended choices to confirm before writing code:

1. **Ingestion style**
   - Recommended: async background job with polling
   - Alternative: synchronous request/response

2. **Clone ownership**
   - Recommended: backend clones the repo, analysis reads shared workspace
   - Alternative: analysis service clones directly

3. **Python framework**
   - Recommended: FastAPI

4. **Phase 1 graph depth**
   - Recommended: file tree + manifests only
   - Alternative: also attempt basic import/dependency edges now

5. **Workspace structure**
   - Recommended: parent folder containing 3 repos + `infra/`
   - Alternative: fully independent repos with separate docs

## Recommended Default Direction

Recommended implementation defaults:

- async jobs
- backend-owned clone
- FastAPI for the analysis service
- file tree + manifest graph scope for Phase 1
- shared parent workspace with `infra/`
