# GitHub Graph: Repository Intelligence Platform

## Problem

Understanding an unfamiliar codebase or diagnosing a failure is slow because
source files, call paths, imports, and runtime evidence are disconnected.
GitHub Graph turns a public Python repository into a queryable dependency model
and presents impact, similarity, localization, and grounded explanations in one
workspace.

## What it does

- Validates, clones, and indexes one public GitHub repository at a time.
- Extracts Python files, classes, functions, imports, calls, inheritance, API
  routes, and module dependencies.
- Builds a Neo4j graph with repository, file, class, function, route, and
  module nodes plus dependency edges.
- Runs DFS, BFS impact tracing, component analysis, topological ordering,
  centrality, and cycle detection.
- Ranks similar functions and likely root causes from stack traces and durable
  failure history.
- Produces cited, evidence-bounded AI explanations rather than ungrounded
  summaries.

## Architecture and stack

Next.js provides the interactive graph workspace. Spring Boot owns validation,
job orchestration, authentication, PostgreSQL persistence, Neo4j persistence,
and public APIs. Python performs static analysis, graph algorithms, similarity,
localization, and grounded-query orchestration. Docker Compose runs the full
stack locally.

See the [architecture diagram](ARCHITECTURE_DIAGRAM.md).

## Evidence of engineering depth

- Public-repository validation, clone time/size limits, and Python file/source
  limits protect the ingestion pipeline.
- Stable, deduplicated graph identities allow snapshot-scoped graph queries.
- Confirmed root causes persist and influence future localization ranking.
- Authentication, saved repositories, exports, retries, and health checks make
  the project demonstrably deployable as a single-instance service.
- AI requests use bounded evidence, cited graph references, provider retries,
  and prompt-injection handling.

## Resume-ready description

Built a GitHub repository intelligence platform using Spring Boot, Python,
PostgreSQL, Neo4j, Docker, and graph algorithms to extract Python code
dependencies, perform impact analysis and root-cause localization, and deliver
evidence-grounded AI explanations through an interactive Next.js workspace.

## Resume bullets

- Designed a Python static-analysis pipeline that converts source files,
  symbols, imports, calls, inheritance, routes, and modules into a stable Neo4j
  dependency graph.
- Implemented DFS/BFS impact analysis, centrality, cycle detection, Jaccard
  similarity, and stack-trace localization behind Spring Boot APIs.
- Built a full-stack repository workspace with graph exploration, persisted
  failure history, cited AI explanations, authentication, retryable jobs, and
  JSON/PDF exports.

## Demo and evidence

- [Demo video storyboard](DEMO_VIDEO.md)
- [Benchmark snapshot](BENCHMARKS.md)
- [Sample case studies](CASE_STUDIES.md)
- [Verification guide](../STAGE_4_VERIFICATION.md)
