# GitHub Graph

Parent workspace for the GitHub Graph Phase 1 foundation.

## Workspace layout

- [PHASE_1_SOLUTIONING.md](/Users/srishtitayal/Projects/GitHub-Graph/PHASE_1_SOLUTIONING.md)
- [github-graph-web](/Users/srishtitayal/Projects/GitHub-Graph/github-graph-web)
- [github-graph-api](/Users/srishtitayal/Projects/GitHub-Graph/github-graph-api)
- [github-graph-analysis](/Users/srishtitayal/Projects/GitHub-Graph/github-graph-analysis)
- [infra](/Users/srishtitayal/Projects/GitHub-Graph/infra)

## Current status

This workspace now contains:

- approved Phase 1 architecture documentation
- repo scaffolding for frontend, backend, analysis engine, and infra
- starter API and analysis service contracts
- Phase 2 ingestion pipeline implementation
- Phase 3 Python static code extraction
- Phase 4 Neo4j-backed repository graph storage and retrieval
- initial PostgreSQL migration
- Docker Compose setup for local development

## Phase documents

- [PHASE_1_SOLUTIONING.md](/Users/srishtitayal/Projects/GitHub-Graph/PHASE_1_SOLUTIONING.md)
- [PHASE_2.md](/Users/srishtitayal/Projects/GitHub-Graph/PHASE_2.md)
- [PHASE_4.md](/Users/srishtitayal/Projects/GitHub-Graph/PHASE_4.md)
- Phase 4 code graph implementation and documentation

## Next build steps

1. implement GitHub URL validation - done
2. implement backend clone workflow
3. connect backend to FastAPI analysis service
4. persist job and snapshot state in PostgreSQL
5. write initial file tree and manifest graph into Neo4j
6. review Phase 4 graph docs in [PHASE_4.md](PHASE_4.md)
