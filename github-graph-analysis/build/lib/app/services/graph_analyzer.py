"""
Graph analytics algorithms for code dependency analysis.

Provides DFS, BFS, connected components, topological sort, centrality analysis,
and cycle detection.
"""

from collections import defaultdict, deque
from dataclasses import dataclass
from typing import Any

from app.schemas.responses import GraphEdge, GraphNode, GraphPayload


@dataclass
class DependencyPath:
    """Represents a path in the dependency graph."""

    source: str
    target: str
    path: list[str]
    distance: int


@dataclass
class CriticalityScore:
    """Criticality metrics for a node."""

    node_id: str
    node_label: str
    node_type: str
    in_degree: int
    out_degree: int
    betweenness: float
    closeness: float
    importance: float


@dataclass
class CyclePath:
    """Represents a circular dependency."""

    nodes: list[str]
    length: int


class GraphAnalyzer:
    """Analytics engine for code dependency graphs."""

    def __init__(self, graph: GraphPayload) -> None:
        self.graph = graph
        self.nodes_by_id = {node.id: node for node in graph.nodes}

        self.adjacency: dict[str, list[tuple[str, GraphEdge]]] = defaultdict(list)
        self.reverse_adjacency: dict[str, list[tuple[str, GraphEdge]]] = defaultdict(list)

        for edge in graph.edges:
            self.adjacency[edge.source].append((edge.target, edge))
            self.reverse_adjacency[edge.target].append((edge.source, edge))

    def _node_label(self, node_id: str) -> str | None:
        node = self.nodes_by_id.get(node_id)
        return node.label if node else None

    def _node_type(self, node_id: str) -> str | None:
        node = self.nodes_by_id.get(node_id)
        return node.type if node else None

    def _serialize_node(self, node_id: str) -> dict[str, Any]:
        return {
            "id": node_id,
            "label": self._node_label(node_id),
            "type": self._node_type(node_id),
        }

    def dfs_trace_dependencies(
        self, source_id: str, max_depth: int = 10
    ) -> dict[str, Any]:
        """Trace transitive dependencies from a source node using DFS."""
        if source_id not in self.nodes_by_id:
            return {"error": "source_id not found"}

        visited: set[str] = set()
        stack: list[tuple[str, list[str]]] = [(source_id, [source_id])]
        all_paths: list[DependencyPath] = []
        max_distance = 0

        while stack:
            current, path = stack.pop()
            if current in visited:
                continue
            visited.add(current)

            distance = len(path) - 1
            max_distance = max(max_distance, distance)

            if distance < max_depth:
                for neighbor, _ in self.adjacency[current]:
                    if neighbor not in visited:
                        new_path = path + [neighbor]
                        all_paths.append(
                            DependencyPath(
                                source=source_id,
                                target=neighbor,
                                path=new_path,
                                distance=len(new_path) - 1,
                            )
                        )
                        stack.append((neighbor, new_path))

        return {
            "source": source_id,
            "source_label": self._node_label(source_id),
            "source_type": self._node_type(source_id),
            "total_dependencies": len(all_paths),
            "max_depth": max_distance,
            "dependency_paths": [
                {
                    "target": path.target,
                    "target_label": self._node_label(path.target),
                    "target_type": self._node_type(path.target),
                    "distance": path.distance,
                    "path": [self._serialize_node(node_id) for node_id in path.path],
                }
                for path in all_paths
            ],
        }

    def bfs_impact_spread(self, source_id: str, max_depth: int = 5) -> dict[str, Any]:
        """Compute upstream impact spread from a source node using BFS."""
        if source_id not in self.nodes_by_id:
            return {"error": "source_id not found"}

        visited: set[str] = set()
        queue: deque[tuple[str, int]] = deque([(source_id, 0)])
        impact_by_distance: dict[int, set[str]] = defaultdict(set)
        impact_by_distance[0].add(source_id)

        while queue:
            current, distance = queue.popleft()

            if current in visited:
                continue
            visited.add(current)

            if distance < max_depth:
                for neighbor, _ in self.reverse_adjacency[current]:
                    if neighbor not in visited:
                        impact_by_distance[distance + 1].add(neighbor)
                        queue.append((neighbor, distance + 1))

        return {
            "source": source_id,
            "source_label": self._node_label(source_id),
            "source_type": self._node_type(source_id),
            "total_affected": len(visited),
            "impact_radius": max(impact_by_distance.keys()) if impact_by_distance else 0,
            "impact_by_distance": {
                str(distance): {
                    "count": len(nodes),
                    "nodes": [
                        self._serialize_node(node_id) for node_id in sorted(nodes)
                    ],
                }
                for distance, nodes in sorted(impact_by_distance.items())
            },
        }

    def find_connected_components(self) -> dict[str, Any]:
        """Find connected components using union-find."""
        parent: dict[str, str] = {}
        rank: dict[str, int] = {}

        def find(node_id: str) -> str:
            if node_id not in parent:
                parent[node_id] = node_id
                rank[node_id] = 0
            if parent[node_id] != node_id:
                parent[node_id] = find(parent[node_id])
            return parent[node_id]

        def union(left: str, right: str) -> None:
            root_left, root_right = find(left), find(right)
            if root_left == root_right:
                return
            if rank[root_left] < rank[root_right]:
                parent[root_left] = root_right
            elif rank[root_left] > rank[root_right]:
                parent[root_right] = root_left
            else:
                parent[root_right] = root_left
                rank[root_left] += 1

        for edge in self.graph.edges:
            union(edge.source, edge.target)

        for node in self.graph.nodes:
            find(node.id)

        components: dict[str, list[str]] = defaultdict(list)
        for node_id, root in parent.items():
            components[root].append(node_id)

        grouped = sorted(components.values(), key=len, reverse=True)
        return {
            "total_components": len(grouped),
            "largest_component_size": len(grouped[0]) if grouped else 0,
            "components": [
                {
                    "size": len(node_ids),
                    "node_types": sorted(
                        {
                            node_type
                            for node_id in node_ids
                            if (node_type := self._node_type(node_id)) is not None
                        }
                    ),
                    "nodes": [
                        self._serialize_node(node_id) for node_id in sorted(node_ids)
                    ],
                }
                for node_ids in grouped
            ],
        }

    def topological_sort(self) -> dict[str, Any]:
        """Compute topological ordering of the dependency graph."""
        in_degree: dict[str, int] = {node.id: 0 for node in self.graph.nodes}

        for edge in self.graph.edges:
            in_degree[edge.target] = in_degree.get(edge.target, 0) + 1

        queue: deque[str] = deque(
            node_id for node_id, degree in in_degree.items() if degree == 0
        )
        topo_order: list[str] = []

        while queue:
            node_id = queue.popleft()
            topo_order.append(node_id)

            for neighbor, _ in self.adjacency[node_id]:
                in_degree[neighbor] -= 1
                if in_degree[neighbor] == 0:
                    queue.append(neighbor)

        has_cycle = len(topo_order) != len(self.graph.nodes)
        unordered_ids = [
            node.id for node in self.graph.nodes if node.id not in topo_order
        ]

        return {
            "is_acyclic": not has_cycle,
            "ordering": [
                {
                    "position": index,
                    **self._serialize_node(node_id),
                }
                for index, node_id in enumerate(topo_order)
            ],
            "total_ordered": len(topo_order),
            "unordered_nodes": [
                self._serialize_node(node_id) for node_id in unordered_ids
            ],
        }

    def compute_centrality(self) -> dict[str, Any]:
        """Compute centrality metrics for all nodes."""
        scores: dict[str, CriticalityScore] = {}

        for node in self.graph.nodes:
            in_degree = len(self.reverse_adjacency[node.id])
            out_degree = len(self.adjacency[node.id])
            betweenness = in_degree * out_degree
            max_distance = max(
                (self._bfs_distance(node.id, other.id) for other in self.graph.nodes),
                default=1,
            )
            closeness = (in_degree + 1) / (1 + max_distance)
            importance = (
                in_degree * 0.4
                + out_degree * 0.3
                + betweenness * 0.2
                + closeness * 0.1
            )

            scores[node.id] = CriticalityScore(
                node_id=node.id,
                node_label=node.label,
                node_type=node.type,
                in_degree=in_degree,
                out_degree=out_degree,
                betweenness=float(betweenness),
                closeness=float(closeness),
                importance=float(importance),
            )

        sorted_scores = sorted(scores.values(), key=lambda score: score.importance, reverse=True)

        def serialize(score: CriticalityScore) -> dict[str, Any]:
            return {
                "id": score.node_id,
                "label": score.node_label,
                "type": score.node_type,
                "in_degree": score.in_degree,
                "out_degree": score.out_degree,
                "betweenness": score.betweenness,
                "closeness": score.closeness,
                "importance": score.importance,
            }

        return {
            "total_nodes_analyzed": len(scores),
            "top_critical_nodes": [serialize(score) for score in sorted_scores[:20]],
            "all_scores": [serialize(score) for score in sorted_scores],
        }

    def detect_cycles(self) -> dict[str, Any]:
        """Detect circular dependencies in the graph."""
        visited: set[str] = set()
        rec_stack: set[str] = set()
        cycles: list[CyclePath] = []

        def dfs_cycle(node_id: str, path: list[str]) -> None:
            visited.add(node_id)
            rec_stack.add(node_id)
            path.append(node_id)

            for neighbor, _ in self.adjacency[node_id]:
                if neighbor not in visited:
                    dfs_cycle(neighbor, path[:])
                elif neighbor in rec_stack:
                    cycle_start = path.index(neighbor)
                    cycle_nodes = path[cycle_start:] + [neighbor]
                    cycles.append(
                        CyclePath(nodes=cycle_nodes, length=len(cycle_nodes) - 1)
                    )

            rec_stack.discard(node_id)

        for node in self.graph.nodes:
            if node.id not in visited:
                dfs_cycle(node.id, [])

        unique_cycles: list[CyclePath] = []
        seen: set[tuple[str, ...]] = set()
        for cycle in cycles:
            normalized = tuple(sorted(cycle.nodes[:-1]))
            if normalized not in seen:
                seen.add(normalized)
                unique_cycles.append(cycle)

        return {
            "has_cycles": len(unique_cycles) > 0,
            "total_cycles": len(unique_cycles),
            "cycles": [
                {
                    "length": cycle.length,
                    "nodes": [self._serialize_node(node_id) for node_id in cycle.nodes],
                }
                for cycle in unique_cycles[:50]
            ],
        }

    def _bfs_distance(self, source: str, target: str) -> int:
        if source == target:
            return 0

        visited: set[str] = {source}
        queue: deque[tuple[str, int]] = deque([(source, 0)])

        while queue:
            current, distance = queue.popleft()

            for neighbor, _ in self.adjacency[current]:
                if neighbor == target:
                    return distance + 1
                if neighbor not in visited:
                    visited.add(neighbor)
                    queue.append((neighbor, distance + 1))

        return 999


def resolve_node_id(graph: GraphPayload, node_ref: str) -> str | None:
    """Resolve a node id or label to a graph node id."""
    if any(node.id == node_ref for node in graph.nodes):
        return node_ref

    exact_label_matches = [node.id for node in graph.nodes if node.label == node_ref]
    if len(exact_label_matches) == 1:
        return exact_label_matches[0]
    if len(exact_label_matches) > 1:
        return exact_label_matches[0]

    suffix_matches = [
        node.id
        for node in graph.nodes
        if node.label.endswith(node_ref) or node_ref in node.label
    ]
    if suffix_matches:
        return suffix_matches[0]

    return None


def build_insights(
    analytics: dict[str, Any],
    node_analysis: dict[str, Any] | None = None,
) -> dict[str, Any]:
    """Summarize analytics into actionable repository insights."""
    centrality = analytics["centrality"]
    components = analytics["connected_components"]
    cycles = analytics["cycles"]
    topological = analytics["topological_sort"]

    critical_functions = [
        node
        for node in centrality["top_critical_nodes"]
        if node.get("type") == "function"
    ][:10]
    critical_files = [
        node for node in centrality["top_critical_nodes"] if node.get("type") == "file"
    ][:10]
    tightly_coupled = [
        component
        for component in components["components"]
        if component["size"] > 1
    ][:10]

    insights: dict[str, Any] = {
        "most_critical_functions": critical_functions,
        "most_critical_files": critical_files,
        "tightly_coupled_groups": tightly_coupled,
        "has_circular_dependencies": cycles["has_cycles"],
        "circular_dependency_count": cycles["total_cycles"],
        "dependency_graph_is_acyclic": topological["is_acyclic"],
        "connected_component_count": components["total_components"],
        "largest_connected_component_size": components["largest_component_size"],
    }

    if node_analysis is not None:
        dfs_trace = node_analysis.get("dependency_trace", {})
        bfs_impact = node_analysis.get("impact_spread", {})
        insights["selected_node"] = {
            "id": dfs_trace.get("source") or bfs_impact.get("source"),
            "label": dfs_trace.get("source_label") or bfs_impact.get("source_label"),
            "type": dfs_trace.get("source_type") or bfs_impact.get("source_type"),
            "transitive_dependencies": dfs_trace.get("total_dependencies", 0),
            "upstream_impact_count": bfs_impact.get("total_affected", 0),
            "impact_radius": bfs_impact.get("impact_radius", 0),
        }

    return insights


def analyze_graph(
    graph: GraphPayload,
    node_id: str | None = None,
    max_depth: int = 10,
) -> dict[str, Any]:
    """Execute graph analytics and optional node-specific analysis."""
    analyzer = GraphAnalyzer(graph)

    analytics = {
        "connected_components": analyzer.find_connected_components(),
        "topological_sort": analyzer.topological_sort(),
        "centrality": analyzer.compute_centrality(),
        "cycles": analyzer.detect_cycles(),
    }

    node_analysis = None
    if node_id is not None:
        resolved_id = resolve_node_id(graph, node_id)
        if resolved_id is None:
            return {
                "error": "node_not_found",
                "message": f"No graph node matches '{node_id}'",
                **analytics,
            }

        node_analysis = {
            "node_id": resolved_id,
            "dependency_trace": analyzer.dfs_trace_dependencies(resolved_id, max_depth),
            "impact_spread": analyzer.bfs_impact_spread(resolved_id, max_depth),
        }

    return {
        "insights": build_insights(analytics, node_analysis),
        **analytics,
        "node_analysis": node_analysis,
    }
