"""Structural connected-component grouping for graph projections."""

from __future__ import annotations

from collections import deque

from app.analytics.graph_projection import GraphFilter, GraphProjection, ProjectedNode
from app.analytics.models import AnalyticsNode, Component, ConnectedComponentsResult
from app.analytics.traversal import DEPENDENCY_EDGE_TYPES
from app.schemas.responses import GraphPayload


def find_connected_components(
    graph: GraphPayload | GraphProjection,
    *,
    edge_types: set[str] | frozenset[str] | None = None,
    include_external: bool = False,
) -> ConnectedComponentsResult:
    """Group non-repository nodes using an undirected structural projection."""
    selected_edge_types = (
        frozenset(edge_types) if edge_types is not None else DEPENDENCY_EDGE_TYPES
    )
    projection = _as_projection(graph).filtered(
        GraphFilter.from_values(
            edge_types=selected_edge_types,
            include_external=include_external,
        )
    )
    eligible_node_ids = {
        node_id
        for node_id, node in projection.nodes_by_id.items()
        if node.type != "repo"
    }
    unvisited = set(eligible_node_ids)
    components: list[Component] = []

    while unvisited:
        start_node_id = min(unvisited)
        unvisited.remove(start_node_id)
        component_node_ids: list[str] = []
        queue: deque[str] = deque([start_node_id])

        while queue:
            current_node_id = queue.popleft()
            component_node_ids.append(current_node_id)
            for neighbor_node_id in _neighbors(projection, current_node_id):
                if neighbor_node_id not in unvisited:
                    continue
                unvisited.remove(neighbor_node_id)
                queue.append(neighbor_node_id)

        ordered_node_ids = sorted(component_node_ids)
        component_nodes = [
            _analytics_node(projection.nodes_by_id[node_id])
            for node_id in ordered_node_ids
        ]
        components.append(
            Component(
                componentId=f"component:{len(components) + 1}",
                nodes=component_nodes,
                nodeCount=len(component_nodes),
            )
        )

    return ConnectedComponentsResult(
        components=components,
        componentCount=len(components),
        selectedEdgeTypes=sorted(selected_edge_types),
        includeExternal=include_external,
        reasoningMetadata={
            "edgeInterpretation": "undirected",
            "repositoryRootExcluded": True,
        },
    )


def _as_projection(graph: GraphPayload | GraphProjection) -> GraphProjection:
    return graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)


def _neighbors(projection: GraphProjection, node_id: str) -> tuple[str, ...]:
    neighbor_ids = {
        edge.target for edge in projection.outgoing_edges(node_id)
    } | {
        edge.source for edge in projection.incoming_edges(node_id)
    }
    return tuple(sorted(neighbor_ids))


def _analytics_node(node: ProjectedNode) -> AnalyticsNode:
    return AnalyticsNode(
        nodeId=node.id,
        label=node.label,
        nodeType=node.type,
        properties=dict(node.properties),
    )
