INSERT INTO repositories (
    id,
    github_url,
    owner,
    name,
    default_branch,
    is_public,
    status,
    created_at,
    updated_at,
    last_ingested_at
) VALUES (
    '11111111-1111-1111-1111-111111111111',
    'https://github.com/github-graph/stage2-integration-fixture',
    'github-graph',
    'stage2-integration-fixture',
    'main',
    TRUE,
    'COMPLETED',
    '2026-07-20 10:00:00',
    '2026-07-20 10:00:00',
    '2026-07-20 10:00:00'
);

INSERT INTO ingestion_jobs (
    id,
    repository_id,
    status,
    submitted_url,
    clone_path,
    started_at,
    finished_at,
    created_at,
    updated_at
) VALUES
(
    '44444444-4444-4444-4444-444444444444',
    '11111111-1111-1111-1111-111111111111',
    'COMPLETED',
    'https://github.com/github-graph/stage2-integration-fixture',
    '/tmp/github-graph-integration/snapshot-a',
    '2026-07-20 10:00:00',
    '2026-07-20 10:01:00',
    '2026-07-20 10:00:00',
    '2026-07-20 10:01:00'
),
(
    '55555555-5555-5555-5555-555555555555',
    '11111111-1111-1111-1111-111111111111',
    'COMPLETED',
    'https://github.com/github-graph/stage2-integration-fixture',
    '/tmp/github-graph-integration/snapshot-b',
    '2026-07-20 11:00:00',
    '2026-07-20 11:01:00',
    '2026-07-20 11:00:00',
    '2026-07-20 11:01:00'
);

INSERT INTO repository_snapshots (
    id,
    repository_id,
    ingestion_job_id,
    branch_name,
    commit_sha,
    commit_message,
    commit_author,
    committed_at,
    root_directory,
    total_files,
    total_directories,
    language_summary_json,
    created_at
) VALUES
(
    '22222222-2222-2222-2222-222222222222',
    '11111111-1111-1111-1111-111111111111',
    '44444444-4444-4444-4444-444444444444',
    'main',
    'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
    'Integration snapshot A',
    'GitHub Graph',
    '2026-07-20 10:00:00',
    '/tmp/github-graph-integration/snapshot-a',
    1,
    1,
    '{"Python": 1}',
    '2026-07-20 10:01:00'
),
(
    '33333333-3333-3333-3333-333333333333',
    '11111111-1111-1111-1111-111111111111',
    '55555555-5555-5555-5555-555555555555',
    'main',
    'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
    'Integration snapshot B',
    'GitHub Graph',
    '2026-07-20 11:00:00',
    '/tmp/github-graph-integration/snapshot-b',
    1,
    1,
    '{"Python": 1}',
    '2026-07-20 11:01:00'
);
