"""Prerequisites-first topological sorting for dependency projections."""

from __future__ import annotations

import heapq

from app.analytics.cycles import _detect_cycles_in_projection
from app.analytics.graph_projection import GraphFilter, GraphProjection, ProjectedNode
from app.analytics.models import AnalyticsNode, TopologicalSortResult
from app.schemas.responses import GraphPayload


DEFAULT_TOPOLOGICAL_EDGE_TYPES = frozenset({"IMPORTS", "USES"})


def topological_sort(
    graph: GraphPayload | GraphProjection,
    *,
    edge_types: set[str] | frozenset[str] | None = None,
    include_external: bool = False,
) -> TopologicalSortResult:
    """Return a prerequisites-first order or cycle-aware partial diagnostics.

    Phase 4 dependency edges point from a dependent to its prerequisite. Kahn's
    algorithm therefore starts with nodes whose outgoing dependency count is
    zero and releases their incoming dependents.
    """
    selected_edge_types = (
        frozenset(edge_types)
        if edge_types is not None
        else DEFAULT_TOPOLOGICAL_EDGE_TYPES
    )
    projection = _as_projection(graph).filtered(
        GraphFilter.from_values(
            edge_types=selected_edge_types,
            include_external=include_external,
        )
    )
    remaining_prerequisites = {
        node_id: len(projection.outgoing_edges(node_id))
        for node_id in projection.nodes_by_id
    }
    available_node_ids = [
        node_id
        for node_id, prerequisite_count in remaining_prerequisites.items()
        if prerequisite_count == 0
    ]
    heapq.heapify(available_node_ids)
    ordered_node_ids: list[str] = []

    while available_node_ids:
        prerequisite_node_id = heapq.heappop(available_node_ids)
        ordered_node_ids.append(prerequisite_node_id)
        for edge in projection.incoming_edges(prerequisite_node_id):
            dependent_node_id = edge.source
            remaining_prerequisites[dependent_node_id] -= 1
            if remaining_prerequisites[dependent_node_id] == 0:
                heapq.heappush(available_node_ids, dependent_node_id)

    is_acyclic = len(ordered_node_ids) == len(projection.nodes_by_id)
    partial_order = [
        _analytics_node(projection.nodes_by_id[node_id]) for node_id in ordered_node_ids
    ]
    cycles = _detect_cycles_in_projection(projection) if not is_acyclic else []
    return TopologicalSortResult(
        isAcyclic=is_acyclic,
        topologicalOrder=partial_order if is_acyclic else [],
        partialOrder=partial_order,
        blockedNodeIds=sorted(
            node_id
            for node_id, prerequisite_count in remaining_prerequisites.items()
            if prerequisite_count > 0
        ),
        cycles=cycles,
        selectedEdgeTypes=sorted(selected_edge_types),
        includeExternal=include_external,
        reasoningMetadata={
            "orderDirection": "prerequisites_first",
            "partialOrderReturned": not is_acyclic,
        },
    )


def _as_projection(graph: GraphPayload | GraphProjection) -> GraphProjection:
    return graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)


def _analytics_node(node: ProjectedNode) -> AnalyticsNode:
    return AnalyticsNode(
        nodeId=node.id,
        label=node.label,
        nodeType=node.type,
        properties=dict(node.properties),
    )
