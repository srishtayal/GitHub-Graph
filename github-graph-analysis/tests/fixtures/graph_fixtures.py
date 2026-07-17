"""Small, explicit graph payloads shared by Phase 5 unit tests."""

from app.schemas.responses import GraphEdge, GraphNode, GraphPayload


def primary_graph() -> GraphPayload:
    """A graph with a dependency chain, an import cycle, and an external module."""
    return GraphPayload(
        nodes=[
            _node("repo:sample", "repo", "sample"),
            _node("file:api", "file", "api.py"),
            _node("file:service", "file", "service.py"),
            _node("file:model", "file", "model.py"),
            _node("file:cycle-a", "file", "cycle_a.py"),
            _node("file:cycle-b", "file", "cycle_b.py"),
            _node("file:orphan", "file", "orphan.py"),
            _node("function:handler", "function", "handler"),
            _node("function:process", "function", "process"),
            _node("function:helper", "function", "helper"),
            _node("module:requests", "module", "requests"),
        ],
        edges=[
            _edge("edge:08", "file:cycle-b", "file:cycle-a", "USES"),
            _edge("edge:07", "file:cycle-a", "file:cycle-b", "USES"),
            _edge("edge:06", "function:process", "function:helper", "CALLS"),
            _edge("edge:05", "function:handler", "function:process", "CALLS"),
            _edge("edge:04", "file:service", "file:model", "USES"),
            _edge(
                "edge:03",
                "file:api",
                "module:requests",
                "IMPORTS",
                dependencyType="EXTERNAL",
            ),
            _edge("edge:02", "file:api", "function:handler", "USES"),
            _edge("edge:01", "file:api", "file:service", "USES"),
            _edge("edge:00", "file:api", "repo:sample", "BELONGS_TO"),
        ],
    )


def acyclic_graph() -> GraphPayload:
    """The primary graph with its circular file dependencies removed."""
    graph = primary_graph()
    return GraphPayload(
        nodes=graph.nodes,
        edges=[edge for edge in graph.edges if edge.id not in {"edge:07", "edge:08"}],
    )


def call_cycle_graph() -> GraphPayload:
    """A minimal recursive call graph for optional CALLS-cycle analysis."""
    return GraphPayload(
        nodes=[
            _node("function:a", "function", "a"),
            _node("function:b", "function", "b"),
        ],
        edges=[
            _edge("edge:call-2", "function:b", "function:a", "CALLS"),
            _edge("edge:call-1", "function:a", "function:b", "CALLS"),
        ],
    )


def self_cycle_graph() -> GraphPayload:
    """A minimal self-recursive dependency graph."""
    return GraphPayload(
        nodes=[_node("file:self", "file", "self.py")],
        edges=[_edge("edge:self", "file:self", "file:self", "USES")],
    )


def _node(node_id: str, node_type: str, label: str) -> GraphNode:
    return GraphNode(id=node_id, type=node_type, label=label)


def _edge(
    edge_id: str,
    source: str,
    target: str,
    edge_type: str,
    **properties: str,
) -> GraphEdge:
    return GraphEdge(
        id=edge_id,
        source=source,
        target=target,
        type=edge_type,
        properties=properties,
    )
