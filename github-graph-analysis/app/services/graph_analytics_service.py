"""Python-only façade for Phase 5 graph analytics."""

from app.analytics.centrality import rank_centrality
from app.analytics.components import find_connected_components
from app.analytics.cycles import detect_cycles
from app.analytics.models import (
    CentralityResult,
    ConnectedComponentsResult,
    CycleDetectionResult,
    DependencyTraceResult,
    ImpactAnalysisResult,
    TopologicalSortResult,
)
from app.analytics.topology import topological_sort
from app.analytics.traversal import TraversalDirection, analyze_impact, trace_dependencies
from app.schemas.responses import GraphPayload


class GraphAnalyticsService:
    """Expose stable-node-ID analytics operations over one graph payload."""

    def trace_dependencies(
        self,
        graph: GraphPayload,
        start_node_id: str,
        *,
        direction: TraversalDirection = "dependencies",
        edge_types: set[str] | frozenset[str] | None = None,
        max_depth: int | None = None,
        include_external: bool = False,
    ) -> DependencyTraceResult:
        return trace_dependencies(
            graph,
            start_node_id,
            direction=direction,
            edge_types=edge_types,
            max_depth=max_depth,
            include_external=include_external,
        )

    def analyze_impact(
        self,
        graph: GraphPayload,
        start_node_id: str,
        *,
        edge_types: set[str] | frozenset[str] | None = None,
        max_depth: int | None = None,
        include_external: bool = False,
    ) -> ImpactAnalysisResult:
        return analyze_impact(
            graph,
            start_node_id,
            edge_types=edge_types,
            max_depth=max_depth,
            include_external=include_external,
        )

    def find_connected_components(
        self,
        graph: GraphPayload,
        *,
        edge_types: set[str] | frozenset[str] | None = None,
        include_external: bool = False,
    ) -> ConnectedComponentsResult:
        return find_connected_components(
            graph,
            edge_types=edge_types,
            include_external=include_external,
        )

    def topological_sort(
        self,
        graph: GraphPayload,
        *,
        edge_types: set[str] | frozenset[str] | None = None,
        include_external: bool = False,
    ) -> TopologicalSortResult:
        return topological_sort(
            graph,
            edge_types=edge_types,
            include_external=include_external,
        )

    def rank_centrality(
        self,
        graph: GraphPayload,
        *,
        edge_types: set[str] | frozenset[str] | None = None,
        node_types: set[str] | frozenset[str] | None = None,
        limit: int | None = None,
        include_external: bool = False,
    ) -> CentralityResult:
        return rank_centrality(
            graph,
            edge_types=edge_types,
            node_types=node_types,
            limit=limit,
            include_external=include_external,
        )

    def detect_cycles(
        self,
        graph: GraphPayload,
        *,
        edge_types: set[str] | frozenset[str] | None = None,
        include_external: bool = False,
    ) -> CycleDetectionResult:
        return detect_cycles(
            graph,
            edge_types=edge_types,
            include_external=include_external,
        )
