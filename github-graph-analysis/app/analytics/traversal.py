"""Depth-first dependency tracing and breadth-first impact analysis."""

from __future__ import annotations

from collections import deque
from typing import Literal

from app.analytics.graph_projection import (
    GraphFilter,
    GraphProjection,
    ProjectedEdge,
    ProjectedNode,
)
from app.analytics.models import AnalyticsNode, DependencyTraceResult, ImpactAnalysisResult
from app.schemas.responses import GraphPayload


DEPENDENCY_EDGE_TYPES = frozenset({"CALLS", "IMPORTS", "USES", "INHERITS"})
TraversalDirection = Literal["dependencies", "dependents"]


def trace_dependencies(
    graph: GraphPayload | GraphProjection,
    start_node_id: str,
    *,
    direction: TraversalDirection = "dependencies",
    edge_types: set[str] | frozenset[str] | None = None,
    max_depth: int | None = None,
    include_external: bool = False,
) -> DependencyTraceResult:
    """Trace a dependency chain in deterministic DFS preorder.

    ``direction="dependencies"`` follows outgoing edges, while
    ``direction="dependents"`` follows incoming edges. The starting node is
    included at depth zero.
    """
    _validate_max_depth(max_depth)
    base_projection = _as_projection(graph)
    projection = _filtered_projection(base_projection, edge_types, include_external)
    _validate_query_node(base_projection, projection, start_node_id)

    visited: set[str] = set()
    records: list[AnalyticsNode] = []
    stack: list[tuple[str, int, str | None, str | None]] = [
        (start_node_id, 0, None, None)
    ]

    while stack:
        current_node_id, depth, predecessor_node_id, via_edge_type = stack.pop()
        if current_node_id in visited:
            continue
        visited.add(current_node_id)

        node = projection.node(current_node_id)
        if node is None:  # Protected by _validate_query_node and projection edges.
            continue
        records.append(
            _analytics_node(node, depth, predecessor_node_id, via_edge_type)
        )

        if max_depth is not None and depth >= max_depth:
            continue

        edges = _traversal_edges(projection, current_node_id, direction)
        for edge in reversed(edges):
            next_node_id = _next_node_id(edge, direction)
            if next_node_id not in visited:
                stack.append((next_node_id, depth + 1, current_node_id, edge.type))

    return DependencyTraceResult(
        queryNodeId=start_node_id,
        direction=direction,
        visitedNodes=records,
        dependencyDepth=max((record.depth or 0 for record in records), default=0),
        selectedEdgeTypes=sorted(_selected_edge_types(edge_types)),
        includeExternal=include_external,
        reasoningMetadata={
            "edgeDirection": "outgoing" if direction == "dependencies" else "incoming",
            "maxDepth": max_depth,
            "startNodeIncluded": True,
        },
    )


def analyze_impact(
    graph: GraphPayload | GraphProjection,
    start_node_id: str,
    *,
    edge_types: set[str] | frozenset[str] | None = None,
    max_depth: int | None = None,
    include_external: bool = False,
) -> ImpactAnalysisResult:
    """Find dependent nodes affected by a start node in BFS order.

    Edges are traversed incoming because dependency edges point from a dependent
    node to the node it requires. ``visitedNodes`` includes the start node at
    depth zero; ``impactedNodes`` contains only reachable dependents.
    """
    _validate_max_depth(max_depth)
    base_projection = _as_projection(graph)
    projection = _filtered_projection(base_projection, edge_types, include_external)
    _validate_query_node(base_projection, projection, start_node_id)

    visited = {start_node_id}
    records: list[AnalyticsNode] = []
    queue: deque[tuple[str, int, str | None, str | None]] = deque(
        [(start_node_id, 0, None, None)]
    )

    while queue:
        current_node_id, depth, predecessor_node_id, via_edge_type = queue.popleft()
        node = projection.node(current_node_id)
        if node is None:  # Protected by _validate_query_node and projection edges.
            continue
        records.append(
            _analytics_node(node, depth, predecessor_node_id, via_edge_type)
        )

        if max_depth is not None and depth >= max_depth:
            continue

        for edge in projection.incoming_edges(current_node_id):
            dependent_node_id = edge.source
            if dependent_node_id in visited:
                continue
            visited.add(dependent_node_id)
            queue.append((dependent_node_id, depth + 1, current_node_id, edge.type))

    return ImpactAnalysisResult(
        queryNodeId=start_node_id,
        direction="dependents",
        visitedNodes=records,
        impactedNodes=records[1:],
        dependencyDepth=max((record.depth or 0 for record in records), default=0),
        selectedEdgeTypes=sorted(_selected_edge_types(edge_types)),
        includeExternal=include_external,
        reasoningMetadata={
            "edgeDirection": "incoming",
            "maxDepth": max_depth,
            "startNodeIncluded": True,
            "impactedNodesExcludeStartNode": True,
        },
    )


def _filtered_projection(
    projection: GraphProjection,
    edge_types: set[str] | frozenset[str] | None,
    include_external: bool,
) -> GraphProjection:
    return projection.filtered(
        GraphFilter.from_values(
            edge_types=_selected_edge_types(edge_types),
            include_external=include_external,
        )
    )


def _as_projection(graph: GraphPayload | GraphProjection) -> GraphProjection:
    return graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)


def _selected_edge_types(
    edge_types: set[str] | frozenset[str] | None,
) -> frozenset[str]:
    return frozenset(edge_types) if edge_types is not None else DEPENDENCY_EDGE_TYPES


def _validate_max_depth(max_depth: int | None) -> None:
    if max_depth is not None and max_depth < 0:
        raise ValueError("max_depth must be zero or greater")


def _validate_query_node(
    original_projection: GraphProjection,
    projection: GraphProjection,
    start_node_id: str,
) -> None:
    if not original_projection.contains_node(start_node_id):
        raise ValueError(f"Unknown graph node ID: {start_node_id}")
    if not projection.contains_node(start_node_id):
        raise ValueError(f"Query node excluded by graph filter: {start_node_id}")


def _traversal_edges(
    projection: GraphProjection,
    node_id: str,
    direction: TraversalDirection,
) -> tuple[ProjectedEdge, ...]:
    if direction == "dependencies":
        return projection.outgoing_edges(node_id)
    return projection.incoming_edges(node_id)


def _next_node_id(edge: ProjectedEdge, direction: TraversalDirection) -> str:
    return edge.target if direction == "dependencies" else edge.source


def _analytics_node(
    node: ProjectedNode,
    depth: int,
    predecessor_node_id: str | None,
    via_edge_type: str | None,
) -> AnalyticsNode:
    return AnalyticsNode(
        nodeId=node.id,
        label=node.label,
        nodeType=node.type,
        depth=depth,
        predecessorNodeId=predecessor_node_id,
        viaEdgeType=via_edge_type,
        properties=dict(node.properties),
    )
