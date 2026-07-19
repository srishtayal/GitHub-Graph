"""Reusable Phase 7 requests and model responses."""

import json

from app.analytics.traversal import trace_dependencies
from app.schemas.explanations import ExplanationRequest
from tests.fixtures.graph_fixtures import primary_graph


def dependency_request() -> ExplanationRequest:
    graph = primary_graph()
    return ExplanationRequest(
        query="Explain the dependency flow from the API file",
        repositoryId="repo-sample",
        repositoryMetadata={"name": "sample", "languageSummary": {"Python": 3}},
        graph=graph,
        dependencyTrace=trace_dependencies(graph, "file:api"),
    )


def grounded_dependency_response() -> str:
    return json.dumps(
        {
            "intent": "dependency_flow",
            "answer": "The supplied trace starts at api.py and reaches service.py before model.py.",
            "supportingEvidence": [
                {
                    "evidenceId": "analytics:dependency-trace",
                    "sourceType": "dependencyTrace",
                    "rationale": "It records the DFS traversal order.",
                },
                {
                    "evidenceId": "graph:referenced-nodes",
                    "sourceType": "graph",
                    "rationale": "It identifies the traced nodes.",
                },
            ],
            "referencedNodeIds": ["file:api", "file:service", "file:model"],
            "referencedEdgeIds": ["edge:01", "edge:04"],
            "confidence": "high",
            "limitations": [],
            "followUpSuggestions": [],
        }
    )
