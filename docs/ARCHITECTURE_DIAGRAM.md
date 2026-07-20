# GitHub Graph Architecture

```mermaid
flowchart LR
    U[Developer or recruiter] --> W[Next.js workspace]
    W -->|REST API| A[Spring Boot API]

    A -->|validate public URL| G[GitHub public repository]
    A -->|clone bounded snapshot| C[Persistent clone cache]
    A -->|metadata, jobs, snapshots, users, failures| P[(PostgreSQL)]
    A -->|graph nodes and edges| N[(Neo4j)]
    A -->|analysis, similarity, localization, explanations| S[Python analysis service]

    C --> S
    S -->|Python AST extraction| X[Structured code data]
    X -->|stable IDs and edges| GR[Repository graph]
    GR --> A
    A --> N

    N -->|selected snapshot graph| A
    P -->|failure history and user scope| A
    A -->|bounded graph evidence| S
    S -->|optional grounded request| AI[Gemini]
    S -->|cited answer or controlled failure| A
    A --> W
```

## Request flow

1. The user submits one public GitHub repository through the Next.js workspace.
2. Spring Boot validates visibility, persists an asynchronous job, and clones a
   bounded local snapshot.
3. The Python service parses supported Python files with the standard AST,
   extracts symbols and relationships, then creates stable graph nodes and
   edges.
4. Spring Boot stores relational metadata in PostgreSQL and the connected
   dependency graph in Neo4j.
5. Analytics, similarity, failure localization, and explanations load the
   selected snapshot graph and return structured, evidence-backed results.

## Storage responsibilities

| Store | Responsibility |
| --- | --- |
| PostgreSQL | Users, saved repositories, jobs, snapshots, files, symbols, extraction JSON, and durable failure history. |
| Neo4j | Repository graph nodes and edges optimized for traversal and impact queries. |
| Clone cache | Bounded local repository snapshots reused by the analysis worker. |

The diagram is intentionally technology-specific: it is suitable for a project
report, README preview, or a placement presentation.
