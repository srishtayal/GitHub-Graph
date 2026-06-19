CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    github_url TEXT NOT NULL UNIQUE,
    owner VARCHAR(255) NOT NULL,
    name VARCHAR(255) NOT NULL,
    default_branch VARCHAR(255),
    is_public BOOLEAN NOT NULL,
    status VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_ingested_at TIMESTAMP
);

CREATE TABLE ingestion_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id),
    status VARCHAR(64) NOT NULL,
    submitted_url TEXT NOT NULL,
    clone_path TEXT,
    error_message TEXT,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE repository_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id),
    ingestion_job_id UUID NOT NULL REFERENCES ingestion_jobs(id),
    branch_name VARCHAR(255),
    commit_sha VARCHAR(255),
    commit_message TEXT,
    commit_author VARCHAR(255),
    committed_at TIMESTAMP,
    root_directory TEXT NOT NULL,
    total_files INTEGER NOT NULL,
    total_directories INTEGER NOT NULL,
    language_summary_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE directories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES repository_snapshots(id),
    relative_path TEXT NOT NULL,
    name VARCHAR(255) NOT NULL,
    parent_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES repository_snapshots(id),
    relative_path TEXT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    extension VARCHAR(64),
    language VARCHAR(255),
    size_bytes BIGINT NOT NULL,
    is_binary BOOLEAN NOT NULL,
    checksum VARCHAR(255),
    directory_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE code_symbols (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES repository_snapshots(id),
    file_id UUID REFERENCES files(id),
    symbol_type VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    qualified_name TEXT,
    language VARCHAR(255),
    start_line INTEGER,
    end_line INTEGER,
    parent_symbol_name TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE import_relations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    snapshot_id UUID NOT NULL REFERENCES repository_snapshots(id),
    file_id UUID REFERENCES files(id),
    import_value TEXT NOT NULL,
    import_type VARCHAR(64),
    resolved_path TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE analysis_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ingestion_job_id UUID NOT NULL REFERENCES ingestion_jobs(id),
    snapshot_id UUID REFERENCES repository_snapshots(id),
    result_version VARCHAR(64) NOT NULL,
    payload_json JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
