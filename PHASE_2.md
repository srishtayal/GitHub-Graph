# GitHub Graph - Phase 2

This document captures the approved design for the next implementation phase of **GitHub Graph**: the repository ingestion and analysis foundation.

## Confirmed decisions

- Frontend: **Next.js**
- Backend: **Spring Boot**
- Analysis engine: **Python**
- Database: **PostgreSQL + Neo4j**
- Communication: **REST APIs**
- Auth: **none**
- Repo support: **public GitHub repositories only**
- Repo ownership: **backend clones the repo**
- Analysis reads from a **shared workspace**
- Python project uses **pyproject.toml**
- Deployment: **local + Docker**
- Ingestion style: **async background job**
- Clone retention: **keep cloned repositories**

## Language scope for this phase

- **Python only** for symbol extraction in this phase
- Extract:
  - files
  - directories
  - classes
  - functions
  - imports
- Parser architecture should be designed for future support of:
  - Java
  - JavaScript
  - TypeScript

## 1. Exact architecture for this phase

### Services

1. **Frontend (`github-graph-web`)**
   - Minimal UI
   - Submits GitHub URL
   - Polls ingestion job status
   - Displays basic analysis results

2. **Backend (`github-graph-api`)**
   - Public system entrypoint
   - Validates GitHub URL
   - Enforces public-repo-only rule
   - Creates ingestion job
   - Clones repo into shared workspace
   - Stores metadata in PostgreSQL
   - Calls Python analysis service over REST
   - Stores returned analysis data
   - Exposes status and result endpoints

3. **Analysis engine (`github-graph-analysis`)**
   - Internal REST service only
   - Reads cloned repo from shared workspace
   - Walks directory tree
   - Detects languages
   - Extracts:
     - files
     - directories
     - functions
     - classes
     - imports
   - Returns structured analysis JSON

4. **PostgreSQL**
   - Operational state
   - Repository/job/snapshot/file/symbol/import metadata

5. **Neo4j**
   - Light use in this phase
   - Store file tree and graph-shaped repository structure where it makes sense

### Flow

1. Frontend submits GitHub URL to backend
2. Backend validates and normalizes URL
3. Backend creates `repository` and `ingestion_job`
4. Backend clones repo into shared workspace
5. Backend records clone metadata
6. Backend calls analysis service with:
   - repository id
   - ingestion job id
   - local clone path
7. Python analyzes cloned repo and returns structured JSON
8. Backend stores analysis results in PostgreSQL
9. Backend optionally stores file tree graph in Neo4j
10. Frontend polls backend and displays status/results

### Execution model

Use **async background jobs with polling**.

## 2. Repository folder structure

### `github-graph-web`

```text
github-graph-web/
├── src/
│   ├── app/
│   │   ├── page.tsx
│   │   ├── repositories/
│   │   │   └── [jobId]/
│   │   │       └── page.tsx
│   │   └── layout.tsx
│   ├── components/
│   │   ├── repo-url-form/
│   │   ├── ingestion-status/
│   │   └── result-summary/
│   ├── lib/
│   │   ├── api-client.ts
│   │   ├── types.ts
│   │   └── env.ts
│   └── styles/
└── ...
```

### `github-graph-api`

```text
github-graph-api/
├── src/
│   ├── main/
│   │   ├── java/com/githubgraph/api/
│   │   │   ├── config/
│   │   │   ├── controller/
│   │   │   ├── dto/
│   │   │   ├── domain/
│   │   │   ├── service/
│   │   │   ├── persistence/
│   │   │   ├── client/
│   │   │   ├── job/
│   │   │   ├── util/
│   │   │   └── exception/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
└── ...
```

### `github-graph-analysis`

```text
github-graph-analysis/
├── app/
│   ├── api/
│   │   └── routes/
│   ├── core/
│   ├── schemas/
│   ├── services/
│   ├── parsers/
│   └── main.py
└── ...
```

## 3. PostgreSQL schema

### `repositories`

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

Statuses:

- `PENDING`
- `VALIDATING`
- `CLONING`
- `ANALYZING`
- `STORING`
- `COMPLETED`
- `FAILED`

### `repository_snapshots`

```text
id                    uuid pk
repository_id         uuid fk -> repositories.id
ingestion_job_id      uuid fk -> ingestion_jobs.id
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

### `directories`

```text
id                    uuid pk
snapshot_id           uuid fk -> repository_snapshots.id
relative_path         text not null
name                  varchar not null
parent_path           text null
created_at            timestamp not null
```

### `files`

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
directory_path        text null
created_at            timestamp not null
```

### `code_symbols`

```text
id                    uuid pk
snapshot_id           uuid fk -> repository_snapshots.id
file_id               uuid fk -> files.id
symbol_type           varchar not null
name                  varchar not null
qualified_name        text null
language              varchar null
start_line            integer null
end_line              integer null
parent_symbol_name    text null
created_at            timestamp not null
```

### `import_relations`

```text
id                    uuid pk
snapshot_id           uuid fk -> repository_snapshots.id
file_id               uuid fk -> files.id
import_value          text not null
import_type           varchar null
resolved_path         text null
created_at            timestamp not null
```

### `analysis_results`

```text
id                    uuid pk
ingestion_job_id      uuid fk -> ingestion_jobs.id
snapshot_id           uuid fk -> repository_snapshots.id
result_version        varchar not null
payload_json          jsonb not null
created_at            timestamp not null
```

## 4. REST API contracts

### Frontend -> Backend

#### Submit ingestion

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

#### Get repository summary

`GET /api/v1/repositories/{repositoryId}`

#### Get repository files

`GET /api/v1/repositories/{repositoryId}/files?snapshotId=...`

#### Get repository symbols

`GET /api/v1/repositories/{repositoryId}/symbols?snapshotId=...`

#### Get repository imports

`GET /api/v1/repositories/{repositoryId}/imports?snapshotId=...`

### Backend -> Analysis service

#### Analyze repository

`POST /internal/v1/analysis-jobs`

Request:

```json
{
  "ingestionJobId": "uuid",
  "repositoryId": "uuid",
  "localPath": "/workspace/repos/repository-id/ingestion-job-id",
  "githubUrl": "https://github.com/owner/repo"
}
```

#### Health

`GET /internal/v1/health`

## 5. Workspace and clone directory design

Use a shared root:

```text
/workspace/repos
```

Local equivalent:

```text
/tmp/github-graph/repos
```

Clone path pattern:

```text
/workspace/repos/{repositoryId}/{ingestionJobId}
```

Rules:

- backend clones repositories
- analysis reads from the shared workspace
- cloned repositories are **kept**
- no automatic cleanup in this phase

## 6. Docker Compose design

Services:

- `web`
- `api`
- `analysis`
- `postgres`
- `neo4j`

Shared volume:

```text
repo-cache:/workspace/repos
```

Mounted into:

- `api`
- `analysis`

## 7. Implementation order

1. Finalize DTOs and shared contracts
2. Implement PostgreSQL migrations
3. Implement backend persistence layer
4. Implement backend URL validation
5. Implement backend clone service
6. Implement backend async orchestration
7. Implement Python analysis contract
8. Implement Python filesystem scanning
9. Implement Python symbol/import extraction for Python only
10. Implement backend analysis client
11. Persist analysis results
12. Add minimal Neo4j write path
13. Connect frontend
14. Wire Docker Compose
15. Run smoke test

## 8. Minimal files/classes/modules to create first

### Backend

- `IngestionController.java`
- `RepositoryController.java`
- `CreateIngestionRequest.java`
- `CreateIngestionResponse.java`
- `IngestionJobResponse.java`
- `AnalysisServiceRequest.java`
- `AnalysisServiceResponse.java`
- `GithubUrlValidator.java`
- `RepositoryCloneService.java`
- `IngestionOrchestratorService.java`
- `AnalysisClientService.java`
- `RepositoryEntity.java`
- `IngestionJobEntity.java`
- `RepositorySnapshotEntity.java`
- `FileEntity.java`
- `CodeSymbolEntity.java`
- `ImportRelationEntity.java`

### Python

- `app/main.py`
- `app/api/routes/analysis.py`
- `app/schemas/requests.py`
- `app/schemas/responses.py`
- `app/services/repo_scanner.py`
- `app/services/language_detector.py`
- `app/services/filesystem_extractor.py`
- `app/services/symbol_extractor.py`
- `app/parsers/python_parser.py`

### Frontend

- `src/app/page.tsx`
- `src/app/repositories/[jobId]/page.tsx`
- `src/components/repo-url-form/repo-url-form.tsx`
- `src/components/ingestion-status/ingestion-status.tsx`
- `src/components/result-summary/result-summary.tsx`
- `src/lib/api-client.ts`
- `src/lib/types.ts`
