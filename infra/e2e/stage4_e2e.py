"""Black-box Stage 4 workflow verification against a clean Docker stack."""

from __future__ import annotations

import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import UTC, datetime
from typing import Any


API_BASE_URL = os.environ.get("API_BASE_URL", "http://api:8080").rstrip("/")
WEB_BASE_URL = os.environ.get("WEB_BASE_URL", "http://web:3000").rstrip("/")
REPOSITORY_URL = os.environ.get(
    "STAGE4_REPOSITORY_URL",
    "https://github.com/pallets/itsdangerous",
)
INCLUDE_AI = os.environ.get("STAGE4_INCLUDE_AI", "0").casefold() in {
    "1",
    "true",
    "yes",
}
TERMINAL_JOB_STATUSES = {"COMPLETED", "FAILED"}
APPROVED_EVIDENCE_TYPES = {
    "repository:metadata": "repositoryMetadata",
    "graph:summary": "graph",
    "graph:referenced-nodes": "graph",
    "graph:referenced-edges": "graph",
    "analytics:dependency-trace": "dependencyTrace",
    "analytics:impact-analysis": "impactAnalysis",
    "analytics:centrality": "centrality",
    "similarity:ranking": "similarity",
    "similarity:clusters": "similarity",
    "localization:result": "bugLocalization",
    "topology:cycles": "topology",
    "topology:order": "topology",
}


def request_json(
    method: str,
    path: str,
    payload: dict[str, Any] | None = None,
    *,
    timeout: int = 120,
) -> dict[str, Any]:
    body = None if payload is None else json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(
        f"{API_BASE_URL}{path}",
        data=body,
        method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        response_body = error.read().decode("utf-8", errors="replace")
        raise AssertionError(
            f"{method} {path} returned HTTP {error.code}: {response_body}"
        ) from error


def require(condition: bool, message: str) -> None:
    if not condition:
        raise AssertionError(message)


def step(message: str) -> None:
    print(f"[stage4] {message}", flush=True)


def encoded(value: str) -> str:
    return urllib.parse.quote(value, safe="")


def query(parameters: dict[str, Any]) -> str:
    return urllib.parse.urlencode(parameters)


def verify_web() -> None:
    with urllib.request.urlopen(WEB_BASE_URL, timeout=20) as response:
        body = response.read().decode("utf-8", errors="replace")
        require(response.status == 200, "Frontend did not return HTTP 200")
        require("<html" in body.casefold(), "Frontend did not return HTML")
    step("frontend runtime responded")


def ingest_repository() -> tuple[str, str]:
    submitted = request_json(
        "POST",
        "/api/v1/repositories/ingestions",
        {"githubUrl": REPOSITORY_URL},
    )
    job_id = submitted["jobId"]
    repository_id = submitted["repositoryId"]
    deadline = time.monotonic() + 240
    last_status = submitted["status"]
    while time.monotonic() < deadline:
        job = request_json("GET", f"/api/v1/ingestion-jobs/{job_id}")
        if job["status"] != last_status:
            last_status = job["status"]
            step(f"ingestion status: {last_status}")
        if job["status"] in TERMINAL_JOB_STATUSES:
            require(
                job["status"] == "COMPLETED",
                f"Ingestion failed [{job.get('errorCategory')}]: {job.get('errorMessage')}",
            )
            return repository_id, job_id
        time.sleep(2)
    raise AssertionError("Ingestion did not finish within 240 seconds")


def retrieve_repository_data(repository_id: str) -> tuple[dict, dict, dict]:
    summary = request_json("GET", f"/api/v1/repositories/{repository_id}")
    analysis = request_json("GET", f"/api/v1/repositories/{repository_id}/analysis")
    graph = request_json("GET", f"/api/v1/repositories/{repository_id}/graph")
    files = request_json("GET", f"/api/v1/repositories/{repository_id}/files")
    symbols = request_json("GET", f"/api/v1/repositories/{repository_id}/symbols")
    imports = request_json("GET", f"/api/v1/repositories/{repository_id}/imports")

    require(summary["status"] == "COMPLETED", "Repository is not completed")
    require(summary.get("latestSnapshot"), "Latest snapshot metadata is missing")
    require(files.get("items"), "Persisted file index is empty")
    require(symbols.get("items"), "Persisted symbol index is empty")
    require("items" in imports, "Persisted import index is missing")
    require(analysis.get("codeFiles"), "Structured code extraction is empty")
    require(analysis.get("functions"), "No Python functions were extracted")
    require(graph.get("nodes"), "Persisted Neo4j graph has no nodes")
    require(graph.get("edges"), "Persisted Neo4j graph has no edges")

    extracted_ids = {node["id"] for node in analysis["graph"]["nodes"]}
    persisted_ids = {node["id"] for node in graph["nodes"]}
    require(
        extracted_ids == persisted_ids,
        "Persisted graph node IDs do not match extracted graph node IDs",
    )
    step(
        "retrieved extraction and graph "
        f"({len(graph['nodes'])} nodes, {len(graph['edges'])} edges)"
    )
    return summary, analysis, graph


def select_function_nodes(graph: dict) -> tuple[dict, dict, dict]:
    nodes = {node["id"]: node for node in graph["nodes"]}
    functions = [node for node in graph["nodes"] if node["type"] == "function"]
    require(len(functions) >= 2, "Stage 4 requires at least two function nodes")
    call_edges = [
        edge
        for edge in graph["edges"]
        if edge["type"] == "CALLS"
        and edge["source"] in nodes
        and edge["target"] in nodes
    ]
    if call_edges:
        dependency_node = nodes[call_edges[0]["source"]]
        impact_node = nodes[call_edges[0]["target"]]
    else:
        dependency_node = functions[0]
        impact_node = functions[0]
    return dependency_node, impact_node, functions[0]


def run_phase_five_analytics(
    repository_id: str,
    dependency_node_id: str,
    impact_node_id: str,
) -> dict[str, dict]:
    repository_query = query({"repositoryId": repository_id})
    calls = {
        "dependencyTrace": (
            f"/api/v1/analytics/path/{encoded(dependency_node_id)}?{repository_query}"
        ),
        "impact": f"/api/v1/analytics/impact/{encoded(impact_node_id)}?{repository_query}",
        "components": f"/api/v1/analytics/components?{repository_query}",
        "cycles": f"/api/v1/analytics/cycles?{repository_query}",
        "topologicalOrder": f"/api/v1/analytics/topological-order?{repository_query}",
        "criticality": f"/api/v1/analytics/critical?{repository_query}&limit=20",
    }
    results = {name: request_json("GET", path) for name, path in calls.items()}
    require(
        results["dependencyTrace"]["startNodeId"] == dependency_node_id,
        "DFS did not start from the requested function",
    )
    require(results["dependencyTrace"]["traversalOrder"], "DFS returned no nodes")
    require(
        results["impact"]["startNodeId"] == impact_node_id,
        "BFS did not start from the requested function",
    )
    require(results["impact"]["affectedNodes"], "BFS returned no nodes")
    require(results["components"]["totalComponents"] >= 1, "No connected components returned")
    require("hasCycles" in results["cycles"], "Cycle result is malformed")
    require("acyclic" in results["topologicalOrder"], "Topological result is malformed")
    require(results["criticality"]["nodes"], "Centrality returned no critical nodes")
    step("all six Phase 5 public analytics endpoints passed")
    return results


def find_similar_functions(repository_id: str, node_id: str) -> dict:
    result = request_json(
        "GET",
        (
            f"/api/v1/intelligence/similarity/{encoded(node_id)}?"
            f"{query({'repositoryId': repository_id, 'limit': 10})}"
        ),
    )
    require(result["targetNodeId"] == node_id, "Similarity target does not match")
    require(result["nodeType"] == "function", "Similarity did not use function profile")
    require(result["results"], "No similar functions were returned")
    require(
        all(item["candidateNodeId"] != node_id for item in result["results"]),
        "Similarity returned its target as a candidate",
    )
    step("Phase 6 public function similarity passed")
    return result


def stack_trace_for(node: dict) -> str:
    properties = node.get("properties", {})
    relative_path = properties.get("relativePath", "unknown.py")
    line = properties.get("startLine", 1)
    name = properties.get("name") or node["label"]
    return (
        f'File "/workspace/repository/{relative_path}", line {line}, in {name}\n'
        "RuntimeError: deterministic stage four verification failure"
    )


def localization_payload(
    repository_id: str,
    snapshot_id: str,
    node_id: str,
    stack_trace: str,
) -> dict:
    return {
        "repositoryId": repository_id,
        "snapshotId": snapshot_id,
        "failingNodeId": node_id,
        "errorLog": "RuntimeError: deterministic stage four verification failure",
        "stackTrace": stack_trace,
        "failurePathNodeIds": [node_id],
        "configuration": {
            "maxTraversalDepth": 2,
            "maxPastFailures": 10,
            "maxSuspectedRootCauses": 10,
        },
    }


def candidate(result: dict, node_id: str) -> dict:
    match = next(
        (item for item in result["suspectedRootCauses"] if item["nodeId"] == node_id),
        None,
    )
    require(match is not None, f"Expected root-cause candidate {node_id}")
    return match


def verify_failure_feedback(
    repository_id: str,
    snapshot_id: str,
    failure_node: dict,
) -> tuple[str, str]:
    stack_trace = stack_trace_for(failure_node)
    request = localization_payload(
        repository_id,
        snapshot_id,
        failure_node["id"],
        stack_trace,
    )
    baseline = request_json("POST", "/api/v1/intelligence/failures/localize", request)
    require(baseline["suspectedRootCauses"], "Localization returned no candidates")
    confirmed_node_id = baseline["suspectedRootCauses"][0]["nodeId"]
    baseline_score = candidate(baseline, confirmed_node_id)["score"]

    created = request_json(
        "POST",
        f"/api/v1/repositories/{repository_id}/failures",
        {
            "snapshotId": snapshot_id,
            "failingNodeId": failure_node["id"],
            "errorLog": request["errorLog"],
            "stackTrace": stack_trace,
            "failurePathNodeIds": [failure_node["id"]],
            "occurredAt": datetime.now(UTC).isoformat(),
            "localizationConfiguration": request["configuration"],
        },
    )
    failure_id = created["failureId"]
    updated = request_json(
        "PATCH",
        f"/api/v1/failures/{failure_id}",
        {
            "status": "RESOLVED",
            "confirmedRootCauseNodeIds": [confirmed_node_id],
            "resolutionNotes": "Confirmed by Stage 4 end-to-end verification.",
            "resolvedAt": datetime.now(UTC).isoformat(),
        },
    )
    require(updated["status"] == "RESOLVED", "Failure was not resolved")
    require(
        updated["confirmedRootCauseNodeIds"] == [confirmed_node_id],
        "Confirmed root cause was not persisted",
    )

    later = request_json("POST", "/api/v1/intelligence/failures/localize", request)
    later_candidate = candidate(later, confirmed_node_id)
    reason_kinds = {reason["kind"] for reason in later_candidate["reasons"]}
    require(
        "historical_failure_overlap" in reason_kinds,
        "Confirmed root cause did not influence later localization",
    )
    require(
        later_candidate["score"] > baseline_score,
        "Confirmed root cause did not increase the later localization score",
    )
    failures = request_json(
        "GET",
        (
            f"/api/v1/repositories/{repository_id}/failures?"
            f"{query({'snapshotId': snapshot_id})}"
        ),
    )
    require(
        any(item["failureId"] == failure_id for item in failures["failures"]),
        "Failure history did not persist",
    )
    step("stack localization, root-cause confirmation, and feedback influence passed")
    return stack_trace, failure_id


def validate_explanation(
    response: dict,
    expected_intent: str,
    repository_id: str,
    snapshot_id: str,
    graph_node_ids: set[str],
    graph_edge_ids: set[str],
) -> None:
    require(response["intent"] == expected_intent, "Explanation intent is incorrect")
    require(response["confidence"] != "insufficient", "Explanation evidence was insufficient")
    require(response["answer"].strip(), "Explanation answer is empty")
    require(response["supportingEvidence"], "Explanation has no evidence citations")

    for reference in response["supportingEvidence"]:
        evidence_id = reference["evidenceId"]
        expected_source = APPROVED_EVIDENCE_TYPES.get(evidence_id)
        require(expected_source is not None, f"Unsupported evidence citation: {evidence_id}")
        require(
            reference["sourceType"] == expected_source,
            f"Evidence source mismatch for {evidence_id}",
        )
        require(reference["rationale"].strip(), f"Evidence rationale is empty: {evidence_id}")

    require(
        set(response["referencedNodeIds"]) <= graph_node_ids,
        "Explanation referenced a node outside the persisted graph",
    )
    require(
        set(response["referencedEdgeIds"]) <= graph_edge_ids,
        "Explanation referenced an edge outside the persisted graph",
    )
    require(
        response["snapshotMetadata"]["repositoryId"] == repository_id,
        "Explanation repository metadata is incorrect",
    )
    require(
        response["snapshotMetadata"]["snapshotId"] == snapshot_id,
        "Explanation snapshot metadata is incorrect",
    )
    model = response["modelMetadata"]
    require(model["provider"] == "gemini", "Unexpected explanation provider")
    require(model["model"], "Explanation model metadata is missing")
    require(model["promptVersion"], "Prompt version metadata is missing")
    require(model["orchestrationVersion"], "Orchestration version metadata is missing")

    if expected_intent == "bug_explanation":
        normalized = response["answer"].casefold()
        uncertainty_terms = ("candidate", "likely", "may", "possible", "hypothesis", "suspected")
        require(
            any(term in normalized for term in uncertainty_terms),
            "Bug explanation presented a root cause without hypothesis language",
        )


def ask_explanations(
    repository_id: str,
    snapshot_id: str,
    dependency_node: dict,
    impact_node: dict,
    failure_node: dict,
    stack_trace: str,
    graph: dict,
) -> None:
    graph_node_ids = {node["id"] for node in graph["nodes"]}
    graph_edge_ids = {edge["id"] for edge in graph["edges"]}
    requests = [
        (
            "dependency_flow",
            {
                "repositoryId": repository_id,
                "query": "Explain this dependency flow.",
                "targetNodeId": dependency_node["id"],
            },
        ),
        (
            "impact_analysis",
            {
                "repositoryId": repository_id,
                "query": "What breaks if this function fails?",
                "targetNodeId": impact_node["id"],
            },
        ),
        (
            "bug_explanation",
            {
                "repositoryId": repository_id,
                "query": "Why is this error happening?",
                "targetNodeId": failure_node["id"],
                "stackTrace": stack_trace,
                "errorLog": "RuntimeError: deterministic stage four verification failure",
            },
        ),
    ]
    for expected_intent, payload in requests:
        response = request_json(
            "POST",
            "/api/v1/explanations/query",
            payload,
            timeout=110,
        )
        validate_explanation(
            response,
            expected_intent,
            repository_id,
            snapshot_id,
            graph_node_ids,
            graph_edge_ids,
        )
    step("three grounded AI workflows and graph citation checks passed")


def main() -> None:
    step(f"repository: {REPOSITORY_URL}")
    verify_web()
    repository_id, job_id = ingest_repository()
    step(f"ingestion completed: job={job_id} repository={repository_id}")
    summary, _, graph = retrieve_repository_data(repository_id)
    snapshot_id = summary["latestSnapshot"]["snapshotId"]
    dependency_node, impact_node, similarity_node = select_function_nodes(graph)

    run_phase_five_analytics(
        repository_id,
        dependency_node["id"],
        impact_node["id"],
    )
    find_similar_functions(repository_id, similarity_node["id"])
    stack_trace, failure_id = verify_failure_feedback(
        repository_id,
        snapshot_id,
        impact_node,
    )

    if INCLUDE_AI:
        ask_explanations(
            repository_id,
            snapshot_id,
            dependency_node,
            impact_node,
            impact_node,
            stack_trace,
            graph,
        )
    else:
        step(
            "AI repository queries skipped. Set STAGE4_INCLUDE_AI=1 only after "
            "approving export of bounded public-repository evidence to Gemini."
        )

    report = {
        "status": "passed",
        "repositoryUrl": REPOSITORY_URL,
        "repositoryId": repository_id,
        "snapshotId": snapshot_id,
        "ingestionJobId": job_id,
        "failureId": failure_id,
        "aiVerified": INCLUDE_AI,
    }
    print(json.dumps(report, sort_keys=True))


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        print(f"[stage4] FAILED: {error}", file=sys.stderr, flush=True)
        raise
