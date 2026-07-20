CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE saved_repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    repository_id UUID NOT NULL REFERENCES repositories(id) ON DELETE CASCADE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (user_id, repository_id)
);

ALTER TABLE ingestion_jobs
    ADD COLUMN retry_count INTEGER NOT NULL DEFAULT 0;

CREATE INDEX idx_saved_repositories_user_created
    ON saved_repositories(user_id, created_at DESC);
CREATE INDEX idx_ingestion_jobs_repository_created
    ON ingestion_jobs(repository_id, created_at DESC);
CREATE INDEX idx_repository_snapshots_repository_created
    ON repository_snapshots(repository_id, created_at DESC);
