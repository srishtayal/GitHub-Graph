"""Select and tag only the supplied evidence relevant to a routed intent."""

from dataclasses import dataclass
from typing import Any

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

        graph_nodes = [node for node in request.graph.nodes if node.id in relevant_node_ids]
        if graph_nodes:
            add("graph:referenced-nodes", "graph", {"nodes": [node.model_dump(mode="json") for node in graph_nodes]})
        allowed_nodes = frozenset(node.id for node in graph_nodes)
        graph_edges = [edge for edge in request.graph.edges if edge.source in allowed_nodes and edge.target in allowed_nodes]
        if graph_edges:
            add("graph:referenced-edges", "graph", {"edges": [edge.model_dump(mode="json") for edge in graph_edges]})

        sufficient = bool(items) and intent != "unknown_or_insufficient" and bool(relevant_node_ids)
        missing = None if sufficient else self._missing_description(intent)
        return EvidenceSelection(items, allowed_nodes, frozenset(edge.id for edge in graph_edges), sufficient, missing)

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
