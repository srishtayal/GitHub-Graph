"""Select and tag only the supplied evidence relevant to a routed intent."""

import json
from dataclasses import dataclass
from typing import Any

from app.core.config import ExplanationLimits
from app.schemas.explanations import EvidenceSourceType, ExplanationIntent, ExplanationRequest


@dataclass(frozen=True)
class SelectedEvidence:
    evidence_id: str
    source_type: EvidenceSourceType
    payload: dict[str, Any]


@dataclass(frozen=True)
class EvidenceSelection:
    items: list[SelectedEvidence]
    allowed_node_ids: frozenset[str]
    allowed_edge_ids: frozenset[str]
    sufficient: bool
    missing_description: str | None = None


class EvidenceSelector:
    """Keeps prompts small and makes permitted citations machine-verifiable."""

    def __init__(self, limits: ExplanationLimits | None = None) -> None:
        self._limits = limits or ExplanationLimits.from_environment()

    def select(self, request: ExplanationRequest, intent: ExplanationIntent) -> EvidenceSelection:
        items: list[SelectedEvidence] = []
        relevant_node_ids: set[str] = set()

        def add(evidence_id: str, source_type: EvidenceSourceType, value: Any) -> None:
            items.append(SelectedEvidence(evidence_id, source_type, value.model_dump(mode="json") if hasattr(value, "model_dump") else value))

        if request.repositoryMetadata:
            add("repository:metadata", "repositoryMetadata", request.repositoryMetadata)

        if intent == "repository_structure":
            add("graph:summary", "graph", {"nodeCount": len(request.graph.nodes), "edgeCount": len(request.graph.edges)})
            relevant_node_ids.update(node.id for node in request.graph.nodes if node.type in {"repo", "file", "module"})
        elif intent == "dependency_flow" and request.dependencyTrace:
            add("analytics:dependency-trace", "dependencyTrace", request.dependencyTrace)
            relevant_node_ids.update(node.nodeId for node in request.dependencyTrace.visitedNodes)
        elif intent == "impact_analysis" and request.impactAnalysis:
            add("analytics:impact-analysis", "impactAnalysis", request.impactAnalysis)
            relevant_node_ids.update(node.nodeId for node in request.impactAnalysis.impactedNodes)
            relevant_node_ids.add(request.impactAnalysis.queryNodeId)
        elif intent == "criticality" and request.centrality:
            add("analytics:centrality", "centrality", request.centrality)
            relevant_node_ids.update(score.node.nodeId for score in request.centrality.rankedNodes)
        elif intent == "similarity" and (request.similarityRanking or request.similarityClusters):
            if request.similarityRanking:
                add("similarity:ranking", "similarity", request.similarityRanking)
                relevant_node_ids.add(request.similarityRanking.targetNodeId)
                relevant_node_ids.update(result.candidateNodeId for result in request.similarityRanking.results)
            if request.similarityClusters:
                add("similarity:clusters", "similarity", request.similarityClusters)
                for cluster in request.similarityClusters.clusters:
                    relevant_node_ids.update(cluster.memberNodeIds)
        elif intent == "bug_explanation" and request.bugLocalization:
            add("localization:result", "bugLocalization", request.bugLocalization)
            relevant_node_ids.update(request.bugLocalization.resolvedFailurePath.nodeIds)
            relevant_node_ids.update(request.bugLocalization.impactedNodeIds)
            relevant_node_ids.update(candidate.nodeId for candidate in request.bugLocalization.suspectedRootCauses)
        elif intent == "cycle_or_order" and (request.cycleDetection or request.topologicalSort):
            if request.cycleDetection:
                add("topology:cycles", "topology", request.cycleDetection)
                for cycle in request.cycleDetection.cycles:
                    relevant_node_ids.update(cycle.nodeIds)
            if request.topologicalSort:
                add("topology:order", "topology", request.topologicalSort)
                relevant_node_ids.update(node.nodeId for node in request.topologicalSort.topologicalOrder)
                relevant_node_ids.update(request.topologicalSort.blockedNodeIds)

        for symbol in request.symbols:
            if symbol.qualifiedName and any(symbol.qualifiedName in node.label for node in request.graph.nodes if node.id in relevant_node_ids):
                add(f"symbol:{symbol.relativePath}:{symbol.name}:{symbol.startLine or 0}", "symbol", symbol)

        for index, snippet in enumerate(request.snippets):
            belongs_to_relevant_node = snippet.nodeId in relevant_node_ids if snippet.nodeId else any(
                node.properties.get("relativePath") == snippet.relativePath
                for node in request.graph.nodes
                if node.id in relevant_node_ids
            )
            if belongs_to_relevant_node:
                add(f"snippet:{index}", "snippet", snippet)

        graph_nodes = sorted(
            (node for node in request.graph.nodes if node.id in relevant_node_ids),
            key=lambda node: node.id,
        )[: self._limits.maxReferencedNodes]
        if graph_nodes:
            add("graph:referenced-nodes", "graph", {"nodes": [node.model_dump(mode="json") for node in graph_nodes]})
        allowed_nodes = frozenset(node.id for node in graph_nodes)
        graph_edges = sorted(
            (
                edge
                for edge in request.graph.edges
                if edge.source in allowed_nodes and edge.target in allowed_nodes
            ),
            key=lambda edge: edge.id,
        )[: self._limits.maxReferencedEdges]
        if graph_edges:
            add("graph:referenced-edges", "graph", {"edges": [edge.model_dump(mode="json") for edge in graph_edges]})

        items = self._bound_items(items)
        sufficient = bool(items) and intent != "unknown_or_insufficient" and bool(relevant_node_ids)
        missing = None if sufficient else self._missing_description(intent)
        return EvidenceSelection(items, allowed_nodes, frozenset(edge.id for edge in graph_edges), sufficient, missing)

    def _bound_items(self, items: list[SelectedEvidence]) -> list[SelectedEvidence]:
        selected = items[: self._limits.maxEvidenceItems]
        if not selected:
            return selected
        per_item_budget = max(500, self._limits.maxEvidenceChars // len(selected))
        bounded = [
            SelectedEvidence(item.evidence_id, item.source_type, _bounded_payload(item.payload, per_item_budget))
            for item in selected
        ]
        while _serialized_size(bounded) > self._limits.maxEvidenceChars and len(bounded) > 1:
            bounded.pop()
        return bounded

    @staticmethod
    def _missing_description(intent: ExplanationIntent) -> str:
        required = {
            "dependency_flow": "a DFS dependency-trace result",
            "impact_analysis": "a BFS impact-analysis result",
            "criticality": "a centrality result",
            "similarity": "a similarity ranking or cluster result",
            "bug_explanation": "a bug-localization result",
            "cycle_or_order": "a cycle-detection or topological-sort result",
            "repository_structure": "repository metadata or graph structure evidence",
            "unknown_or_insufficient": "a recognizable question intent and matching graph-analysis evidence",
        }
        return f"Evidence is insufficient: provide {required[intent]}."


def _bounded_payload(payload: dict[str, Any], budget: int) -> dict[str, Any]:
    if len(json.dumps(payload, sort_keys=True, ensure_ascii=True, default=str)) <= budget:
        return payload
    for list_limit in (50, 25, 10, 5, 2):
        compacted = _compact(payload, list_limit, 1000)
        if len(json.dumps(compacted, sort_keys=True, ensure_ascii=True, default=str)) <= budget:
            return {"evidenceTruncated": True, "payload": compacted}
    return {
        "evidenceTruncated": True,
        "summary": "The structured evidence exceeded the configured evidence bound.",
        "availableKeys": sorted(payload),
    }


def _compact(value: Any, list_limit: int, string_limit: int) -> Any:
    if isinstance(value, dict):
        return {key: _compact(item, list_limit, string_limit) for key, item in value.items()}
    if isinstance(value, list):
        return [_compact(item, list_limit, string_limit) for item in value[:list_limit]]
    if isinstance(value, str) and len(value) > string_limit:
        return value[:string_limit] + "[truncated]"
    return value


def _serialized_size(items: list[SelectedEvidence]) -> int:
    return len(
        json.dumps(
            [
                {
                    "evidenceId": item.evidence_id,
                    "sourceType": item.source_type,
                    "payload": item.payload,
                }
                for item in items
            ],
            sort_keys=True,
            ensure_ascii=True,
            default=str,
        )
    )
