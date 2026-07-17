"""Directed dependency-cycle detection with deterministic cycle paths."""

from __future__ import annotations

from dataclasses import dataclass

from app.analytics.graph_projection import GraphFilter, GraphProjection, ProjectedEdge
from app.analytics.models import Cycle, CycleDetectionResult
from app.schemas.responses import GraphPayload


DEFAULT_CYCLE_EDGE_TYPES = frozenset({"IMPORTS", "USES"})


@dataclass
class _DfsFrame:
    node_id: str
    edge_index: int = 0


def detect_cycles(
    graph: GraphPayload | GraphProjection,
    *,
    edge_types: set[str] | frozenset[str] | None = None,
    include_external: bool = False,
) -> CycleDetectionResult:
    """Find directed cycles and return closed node paths for each discovery."""
    selected_edge_types = (
        frozenset(edge_types) if edge_types is not None else DEFAULT_CYCLE_EDGE_TYPES
    )
    projection = _as_projection(graph).filtered(
        GraphFilter.from_values(
            edge_types=selected_edge_types,
            include_external=include_external,
        )
    )
    cycles = _detect_cycles_in_projection(projection)
    return CycleDetectionResult(
        hasCycles=bool(cycles),
        cycles=cycles,
        selectedEdgeTypes=sorted(selected_edge_types),
        includeExternal=include_external,
        reasoningMetadata={
            "edgeInterpretation": "directed",
            "cyclePathIsClosed": True,
        },
    )


def _detect_cycles_in_projection(projection: GraphProjection) -> list[Cycle]:
    state: dict[str, int] = {node_id: 0 for node_id in projection.nodes_by_id}
    discovered_cycles: dict[tuple[tuple[str, ...], tuple[str, ...]], Cycle] = {}

    for root_node_id in sorted(projection.nodes_by_id):
        if state[root_node_id] != 0:
            continue
        _visit_component(projection, root_node_id, state, discovered_cycles)

    return [discovered_cycles[key] for key in sorted(discovered_cycles)]


def _visit_component(
    projection: GraphProjection,
    root_node_id: str,
    state: dict[str, int],
    discovered_cycles: dict[tuple[tuple[str, ...], tuple[str, ...]], Cycle],
) -> None:
    state[root_node_id] = 1
    active_node_ids = [root_node_id]
    active_edge_types: list[str] = []
    active_index = {root_node_id: 0}
    frames = [_DfsFrame(root_node_id)]

    while frames:
        frame = frames[-1]
        outgoing_edges = projection.outgoing_edges(frame.node_id)
        if frame.edge_index >= len(outgoing_edges):
            state[frame.node_id] = 2
            frames.pop()
            active_index.pop(frame.node_id)
            active_node_ids.pop()
            if active_edge_types:
                active_edge_types.pop()
            continue

        edge = outgoing_edges[frame.edge_index]
        frame.edge_index += 1
        target_node_id = edge.target
        target_state = state[target_node_id]
        if target_state == 0:
            state[target_node_id] = 1
            active_index[target_node_id] = len(active_node_ids)
            active_node_ids.append(target_node_id)
            active_edge_types.append(edge.type)
            frames.append(_DfsFrame(target_node_id))
        elif target_state == 1:
            cycle_start_index = active_index[target_node_id]
            cycle = _canonical_cycle(
                active_node_ids[cycle_start_index:] + [target_node_id],
                active_edge_types[cycle_start_index:] + [edge.type],
            )
            key = (tuple(cycle.nodeIds[:-1]), tuple(cycle.edgeTypes))
            discovered_cycles[key] = cycle


def _canonical_cycle(node_ids: list[str], edge_types: list[str]) -> Cycle:
    """Normalize rotations so the same directed cycle is reported once."""
    cycle_nodes = node_ids[:-1]
    rotations = []
    for index in range(len(cycle_nodes)):
        rotated_nodes = cycle_nodes[index:] + cycle_nodes[:index]
        rotated_edge_types = edge_types[index:] + edge_types[:index]
        rotations.append((tuple(rotated_nodes), tuple(rotated_edge_types)))

    canonical_nodes, canonical_edge_types = min(rotations)
    return Cycle(
        nodeIds=[*canonical_nodes, canonical_nodes[0]],
        edgeTypes=list(canonical_edge_types),
    )


def _as_projection(graph: GraphPayload | GraphProjection) -> GraphProjection:
    return graph if isinstance(graph, GraphProjection) else GraphProjection.from_payload(graph)
