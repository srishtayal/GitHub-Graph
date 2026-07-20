# Sample Case Studies

## 1. Mapping an unfamiliar public repository

**Repository:** [`pallets/itsdangerous`](https://github.com/pallets/itsdangerous)

**Question:** How can a developer understand dependencies before editing an
unfamiliar Python codebase?

**Workflow:**

1. Submit the public URL from the GitHub Graph landing page.
2. Wait for the asynchronous ingestion job to complete.
3. Open the graph explorer and inspect files, classes, functions, imports, and
   call edges.
4. Select a function to view dependency tracing, blast radius, centrality, and
   similar functions.

**Observed evidence:** The clean-stack verification created a graph with 349
nodes and 798 edges, then exercised all six graph-analytics endpoints and the
similarity endpoint against that persisted graph.

**Placement takeaway:** The project converts a repository from a directory of
files into an explorable dependency model, which makes codebase onboarding and
change-impact discussions concrete.

## 2. Turning an error report into root-cause candidates

**Scenario:** A developer has a stack trace or error log but does not know
which code path to inspect first.

**Workflow:**

1. In the Error Analysis view, submit the stack trace and optional error log.
2. The service resolves stack frames to graph nodes, traces impact, compares
   structural similarity, and considers snapshot-scoped failure history.
3. Review ranked root-cause candidates, scores, and evidence contributions.
4. Confirm a root-cause node after investigation; later localization requests
   use that persisted feedback.

**Observed evidence:** The Stage 4 verifier submitted graph-derived failure
evidence, received root-cause candidates, confirmed a cause, and verified that
the later localization score increased for the confirmed node.

**Placement takeaway:** This is not a chatbot guess. The result is a hypothesis
with scores and graph evidence, and the user can improve future rankings with
confirmed investigation outcomes.

## 3. Reusing analysis for delivery and review

**Scenario:** A repository was already analyzed and a reviewer needs a current
artifact without rerunning ingestion.

**Workflow:**

1. Submit the same repository URL.
2. The API returns the existing completed job with `reused: true`.
3. Open saved repository history, select a snapshot, and download JSON or PDF.

**Observed evidence:** In the local Phase 9 smoke test, a repeated repository
submission reused the completed scan. Snapshot history, a 340,713-byte JSON
export, and a PDF export were retrieved successfully.

**Placement takeaway:** The product includes lifecycle behavior beyond parsing:
it avoids duplicate work, preserves snapshots, and supports sharing analysis
results.
