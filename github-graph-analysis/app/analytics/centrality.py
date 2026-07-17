"""Normalized degree-centrality ranking for dependency graphs."""

from __future__ import annotations

from app.analytics.graph_projection import GraphFilter, GraphProjection, ProjectedNode
from app.analytics.models import AnalyticsNode, CentralityNodeScore, CentralityResult
from app.analytics.traversal import DEPENDENCY_EDGE_TYPES
from app.schemas.responses import GraphPayload


def rank_centrality(
    graph: GraphPayload | GraphProjection,
    *,
    edge_types: set[str] | frozenset[str] | None = None,
    node_types: set[str] | frozenset[str] | None = None,
    limit: int | None = None,
    include_external: bool = False,
) -> CentralityResult:
    """Rank eligible nodes by normalized total degree centrality.

    The repository root is excluded when no explicit node-type filter is given,
    because it is a containment anchor rather than an analyzable dependency.
    """
    if limit is not None and limit < 0:
        raise ValueError("limit must be zero or greater")

    selected_edge_types = (
        frozenset(edge_types) if edge_types is not None else DEPENDENCY_EDGE_TYPES
    )
    projection = _as_projection(graph).filtered(
        GraphFilter.from_values(
            edge_types=selected_edge_types,
            include_external=include_external,
        )
    )
    eligible_node_ids = _eligible_node_ids(projection, node_types)
    in_degree = {node_id: 0 for node_id in eligible_node_ids}
    out_degree = {node_id: 0 for node_id in eligible_node_ids}

    for edge in projection.edges:
        if edge.source not in eligible_node_ids or edge.target not in eligible_node_ids:
            continue
        out_degree[edge.source] += 1
        in_degree[edge.target] += 1

    denominator = max(len(eligible_node_ids) - 1, 1)
    scores = [
        CentralityNodeScore(
            node=_analytics_node(projection.nodes_by_id[node_id]),
            inDegree=in_degree[node_id],
            outDegree=out_degree[node_id],
            totalDegree=in_degree[node_id] + out_degree[node_id],
            centralityScore=(in_degree[node_id] + out_degree[node_id]) / denominator,
        )
        for node_id in sorted(eligible_node_ids)
    ]
    ranked_nodes = sorted(
        scores,
        key=lambda score: (
            -score.centralityScore,
            -score.inDegree,
            -score.outDegree,
            score.node.nodeId,
        ),
    )
    if limit is not None:
        ranked_nodes = ranked_nodes[:limit]

    return CentralityResult(
        rankedNodes=ranked_nodes,
        eligibleNodeCount=len(eligible_node_ids),
        selectedEdgeTypes=sorted(selected_edge_types),
        selectedNodeTypes=sorted(node_types) if node_types is not None else None,
        includeExternal=include_external,
        reasoningMetadata={
            "metric": "normalized_degree_centrality",
            "formula": "(in_degree + out_degree) / (eligible_node_count - 1)",
            "normalizationDenominator": denominator,
            "repositoryRootExcludedByDefault": node_types is None,
        },
    )


def _as_projection(graph: GraphPayload | GraphProjection) -> GraphProjection:
    return graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)


def _eligible_node_ids(
    projection: GraphProjection,
    node_types: set[str] | frozenset[str] | None,
) -> set[str]:
    if node_types is None:
        return {
            node_id
            for node_id, node in projection.nodes_by_id.items()
            if node.type != "repo"
        }
    return {
        node_id
        for node_id, node in projection.nodes_by_id.items()
        if node.type in node_types
    }


def _analytics_node(node: ProjectedNode) -> AnalyticsNode:
    return AnalyticsNode(
        nodeId=node.id,
        label=node.label,
        nodeType=node.type,
        properties=dict(node.properties),
    )
