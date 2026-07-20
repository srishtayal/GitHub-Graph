CREATE TABLE failure_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories(id),
    snapshot_id UUID NOT NULL REFERENCES repository_snapshots(id),
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    failing_node_id TEXT,
    error_log TEXT,
    occurred_at TIMESTAMP NOT NULL,
    resolution_notes TEXT,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE failure_evidence (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    failure_id UUID NOT NULL UNIQUE REFERENCES failure_records(id) ON DELETE CASCADE,
    stack_trace TEXT,
    exception_type TEXT,
    message_fingerprint TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE failure_path_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    failure_id UUID NOT NULL REFERENCES failure_records(id) ON DELETE CASCADE,
    node_id TEXT NOT NULL,
    position INTEGER NOT NULL,
    source VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (failure_id, node_id)
);

CREATE TABLE failure_root_cause_nodes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    failure_id UUID NOT NULL REFERENCES failure_records(id) ON DELETE CASCADE,
    node_id TEXT NOT NULL,
    confirmed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (failure_id, node_id)
);

CREATE INDEX idx_failure_records_repository_snapshot
    ON failure_records(repository_id, snapshot_id, occurred_at DESC);
CREATE INDEX idx_failure_path_nodes_failure_position
    ON failure_path_nodes(failure_id, position);
CREATE INDEX idx_failure_root_causes_failure
    ON failure_root_cause_nodes(failure_id);
