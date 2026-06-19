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
- initial PostgreSQL migration
- Docker Compose setup for local development

## Next build steps

1. implement GitHub URL validation - done
2. implement backend clone workflow
3. connect backend to FastAPI analysis service
4. persist job and snapshot state in PostgreSQL
5. write initial file tree and manifest graph into Neo4j
