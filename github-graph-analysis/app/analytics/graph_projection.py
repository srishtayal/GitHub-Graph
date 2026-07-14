"""Immutable, deterministic graph indexes for analytics algorithms."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from types import MappingProxyType
from typing import Any, Iterable, Mapping

from app.schemas.responses import GraphEdge, GraphNode, GraphPayload


@dataclass(frozen=True)
class ProjectedNode:
    """An immutable snapshot of a Phase 4 graph node."""

    id: str
    type: str
    label: str
    properties: Mapping[str, Any]


@dataclass(frozen=True)
class ProjectedEdge:
    """An immutable snapshot of a Phase 4 directed graph edge."""

    id: str
    source: str
    target: str
    type: str
    properties: Mapping[str, Any]


@dataclass(frozen=True)
class GraphFilter:
    """Selection criteria for deriving a graph projection.

    ``include_external=False`` omits import edges marked with
    ``dependencyType=EXTERNAL``. It also omits module nodes whose only observed
    incident relations are external imports. Unresolved call targets do not
    carry that metadata in the Phase 4 schema and therefore remain included.
    """

    edge_types: frozenset[str] | None = None
    node_types: frozenset[str] | None = None
    include_external: bool = True

    def __post_init__(self) -> None:
        if self.edge_types is not None:
            object.__setattr__(self, "edge_types", frozenset(self.edge_types))
        if self.node_types is not None:
            object.__setattr__(self, "node_types", frozenset(self.node_types))

    @classmethod
    def from_values(
        cls,
        *,
        edge_types: Iterable[str] | None = None,
        node_types: Iterable[str] | None = None,
        include_external: bool = True,
    ) -> GraphFilter:
        return cls(
            edge_types=frozenset(edge_types) if edge_types is not None else None,
            node_types=frozenset(node_types) if node_types is not None else None,
            include_external=include_external,
        )


@dataclass(frozen=True)
class GraphProjection:
    """Read-only node and adjacency indexes derived from ``GraphPayload``."""

    nodes_by_id: Mapping[str, ProjectedNode]
    edges: tuple[ProjectedEdge, ...]
    outgoing: Mapping[str, tuple[ProjectedEdge, ...]]
    incoming: Mapping[str, tuple[ProjectedEdge, ...]]

    @classmethod
    def from_payload(cls, payload: GraphPayload) -> GraphProjection:
        """Create an immutable and deterministically ordered graph snapshot.

        Invalid payloads with duplicate node/edge IDs or dangling edge endpoints
        are rejected rather than silently producing incomplete analytics.
        """
        nodes: dict[str, ProjectedNode] = {}
        for node in payload.nodes:
            if node.id in nodes:
                raise ValueError(f"Duplicate graph node ID: {node.id}")
            nodes[node.id] = _project_node(node)

        edges: list[ProjectedEdge] = []
        edge_ids: set[str] = set()
        for edge in payload.edges:
            if edge.id in edge_ids:
                raise ValueError(f"Duplicate graph edge ID: {edge.id}")
            if edge.source not in nodes or edge.target not in nodes:
                raise ValueError(
                    f"Graph edge {edge.id} references an unknown endpoint: "
                    f"{edge.source} -> {edge.target}"
                )
            edge_ids.add(edge.id)
            edges.append(_project_edge(edge))

        return cls._create(nodes.values(), edges)

    def contains_node(self, node_id: str) -> bool:
        return node_id in self.nodes_by_id

    def node(self, node_id: str) -> ProjectedNode | None:
        return self.nodes_by_id.get(node_id)

    def outgoing_edges(self, node_id: str) -> tuple[ProjectedEdge, ...]:
        return self.outgoing.get(node_id, ())

    def incoming_edges(self, node_id: str) -> tuple[ProjectedEdge, ...]:
        return self.incoming.get(node_id, ())

    def filtered(self, graph_filter: GraphFilter) -> GraphProjection:
        """Return a new projection limited by node, edge, and external filters."""
        excluded_external_nodes = (
            _external_only_module_node_ids(self) if not graph_filter.include_external else set()
        )
        selected_nodes = [
            node
            for node in self.nodes_by_id.values()
            if node.id not in excluded_external_nodes
            and (graph_filter.node_types is None or node.type in graph_filter.node_types)
        ]
        selected_node_ids = {node.id for node in selected_nodes}
        selected_edges = [
            edge
            for edge in self.edges
            if edge.source in selected_node_ids
            and edge.target in selected_node_ids
            and (graph_filter.edge_types is None or edge.type in graph_filter.edge_types)
            and (graph_filter.include_external or not _is_external_dependency_edge(edge))
        ]
        return self._create(selected_nodes, selected_edges)

    @classmethod
    def _create(
        cls,
        nodes: Iterable[ProjectedNode],
        edges: Iterable[ProjectedEdge],
    ) -> GraphProjection:
        sorted_nodes = sorted(nodes, key=lambda node: node.id)
        nodes_by_id = {node.id: node for node in sorted_nodes}
        sorted_edges = tuple(sorted(edges, key=lambda edge: edge.id))

        outgoing: dict[str, list[ProjectedEdge]] = defaultdict(list)
        incoming: dict[str, list[ProjectedEdge]] = defaultdict(list)
        for node_id in nodes_by_id:
            outgoing[node_id] = []
            incoming[node_id] = []
        for edge in sorted_edges:
            outgoing[edge.source].append(edge)
            incoming[edge.target].append(edge)

        return cls(
            nodes_by_id=MappingProxyType(nodes_by_id),
            edges=sorted_edges,
            outgoing=MappingProxyType(
                {node_id: tuple(edges) for node_id, edges in outgoing.items()}
            ),
            incoming=MappingProxyType(
                {node_id: tuple(edges) for node_id, edges in incoming.items()}
            ),
        )


def _project_node(node: GraphNode) -> ProjectedNode:
    return ProjectedNode(
        id=node.id,
        type=node.type,
        label=node.label,
        properties=_freeze_mapping(node.properties),
    )


def _project_edge(edge: GraphEdge) -> ProjectedEdge:
    return ProjectedEdge(
        id=edge.id,
        source=edge.source,
        target=edge.target,
        type=edge.type,
        properties=_freeze_mapping(edge.properties),
    )


def _freeze_mapping(values: Mapping[str, Any]) -> Mapping[str, Any]:
    return MappingProxyType({key: _freeze_value(value) for key, value in values.items()})


def _freeze_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return _freeze_mapping(value)
    if isinstance(value, list):
        return tuple(_freeze_value(item) for item in value)
    if isinstance(value, tuple):
        return tuple(_freeze_value(item) for item in value)
    if isinstance(value, set):
        return frozenset(_freeze_value(item) for item in value)
    return value


def _is_external_dependency_edge(edge: ProjectedEdge) -> bool:
    return edge.properties.get("dependencyType") == "EXTERNAL"


def _external_only_module_node_ids(projection: GraphProjection) -> set[str]:
    external_node_ids: set[str] = set()
    for node in projection.nodes_by_id.values():
        if node.type != "module":
            continue
        incident_edges = projection.incoming_edges(node.id) + projection.outgoing_edges(node.id)
        if incident_edges and all(_is_external_dependency_edge(edge) for edge in incident_edges):
            external_node_ids.add(node.id)
    return external_node_ids
