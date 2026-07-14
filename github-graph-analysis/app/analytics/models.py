"""Structured result contracts for Phase 5 graph analytics.

These models deliberately contain no traversal or persistence behavior. Algorithm
modules will populate them from the existing Phase 4 ``GraphPayload`` contract.
"""

from typing import Any, Literal

from pydantic import BaseModel, Field


class AnalyticsNode(BaseModel):
    """A graph node annotated with its position in an analytics result."""

    nodeId: str
    label: str
    nodeType: str
    depth: int | None = None
    predecessorNodeId: str | None = None
    viaEdgeType: str | None = None
    properties: dict[str, Any] = Field(default_factory=dict)


class TraversalResult(BaseModel):
    """Common result fields for graph traversals."""

    queryNodeId: str
    traversalType: Literal["DFS", "BFS"]
    direction: Literal["dependencies", "dependents"]
    visitedNodes: list[AnalyticsNode]
    dependencyDepth: int
    selectedEdgeTypes: list[str]
    includeExternal: bool
    reasoningMetadata: dict[str, Any] = Field(default_factory=dict)


class DependencyTraceResult(TraversalResult):
    """DFS dependency-tracing result."""

    traversalType: Literal["DFS"] = "DFS"


class ImpactAnalysisResult(TraversalResult):
    """BFS dependent-impact result."""

    traversalType: Literal["BFS"] = "BFS"
    impactedNodes: list[AnalyticsNode]


class Component(BaseModel):
    """One weakly connected structural group."""

    componentId: str
    nodes: list[AnalyticsNode]
    nodeCount: int


class ConnectedComponentsResult(BaseModel):
    """Structural grouping result for an entire graph projection."""

    components: list[Component]
    componentCount: int
    selectedEdgeTypes: list[str]
    includeExternal: bool
    reasoningMetadata: dict[str, Any] = Field(default_factory=dict)


class Cycle(BaseModel):
    """A directed cycle represented by its closed node path."""

    nodeIds: list[str]
    edgeTypes: list[str]


class CycleDetectionResult(BaseModel):
    """Directed-cycle analysis result."""

    hasCycles: bool
    cycles: list[Cycle]
    selectedEdgeTypes: list[str]
    includeExternal: bool
    reasoningMetadata: dict[str, Any] = Field(default_factory=dict)


class TopologicalSortResult(BaseModel):
    """Prerequisites-first dependency order and cycle-aware diagnostics."""

    isAcyclic: bool
    topologicalOrder: list[AnalyticsNode]
    partialOrder: list[AnalyticsNode]
    blockedNodeIds: list[str]
    cycles: list[Cycle]
    selectedEdgeTypes: list[str]
    includeExternal: bool
    reasoningMetadata: dict[str, Any] = Field(default_factory=dict)


class CentralityNodeScore(BaseModel):
    """Degree-centrality evidence for one eligible graph node."""

    node: AnalyticsNode
    inDegree: int
    outDegree: int
    totalDegree: int
    centralityScore: float


class CentralityResult(BaseModel):
    """Ranked degree-centrality result for a graph projection."""

    rankedNodes: list[CentralityNodeScore]
    eligibleNodeCount: int
    selectedEdgeTypes: list[str]
    selectedNodeTypes: list[str] | None = None
    includeExternal: bool
    reasoningMetadata: dict[str, Any] = Field(default_factory=dict)
