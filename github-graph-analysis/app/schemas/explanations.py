"""Input and output contracts for grounded Phase 7 explanations."""

from typing import Any, Literal

from pydantic import BaseModel, Field

from app.analytics.models import (
    CentralityResult,
    CycleDetectionResult,
    DependencyTraceResult,
    ImpactAnalysisResult,
    TopologicalSortResult,
)
from app.schemas.failure_analysis import BugLocalizationResult, HistoricalFailure
from app.schemas.responses import GraphPayload, SymbolMetadata
from app.schemas.similarity import ClusterResult, SimilarityRanking


ExplanationIntent = Literal[
    "repository_structure",
    "dependency_flow",
    "impact_analysis",
    "criticality",
    "similarity",
    "bug_explanation",
    "cycle_or_order",
    "unknown_or_insufficient",
]
EvidenceSourceType = Literal[
    "repositoryMetadata",
    "graph",
    "dependencyTrace",
    "impactAnalysis",
    "centrality",
    "similarity",
    "bugLocalization",
    "topology",
    "symbol",
    "snippet",
]
ConfidenceLevel = Literal["high", "medium", "low", "insufficient"]


class RelevantSnippet(BaseModel):
    """Optional source excerpt supplied by an earlier phase or caller."""

    nodeId: str | None = None
    relativePath: str
    startLine: int | None = None
    endLine: int | None = None
    content: str


class ExplanationRequest(BaseModel):
    """One question and only precomputed repository intelligence evidence."""

    query: str = Field(min_length=1)
    repositoryId: str
    repositoryMetadata: dict[str, Any] = Field(default_factory=dict)
    graph: GraphPayload
    dependencyTrace: DependencyTraceResult | None = None
    impactAnalysis: ImpactAnalysisResult | None = None
    centrality: CentralityResult | None = None
    similarityRanking: SimilarityRanking | None = None
    similarityClusters: ClusterResult | None = None
    bugLocalization: BugLocalizationResult | None = None
    cycleDetection: CycleDetectionResult | None = None
    topologicalSort: TopologicalSortResult | None = None
    symbols: list[SymbolMetadata] = Field(default_factory=list)
    snippets: list[RelevantSnippet] = Field(default_factory=list)


class EvidenceReference(BaseModel):
    evidenceId: str
    sourceType: EvidenceSourceType
    rationale: str = Field(min_length=1)


class ExplanationResponse(BaseModel):
    intent: ExplanationIntent
    answer: str = Field(min_length=1)
    supportingEvidence: list[EvidenceReference] = Field(default_factory=list)
    referencedNodeIds: list[str] = Field(default_factory=list)
    referencedEdgeIds: list[str] = Field(default_factory=list)
    confidence: ConfidenceLevel
    limitations: list[str] = Field(default_factory=list)
    followUpSuggestions: list[str] = Field(default_factory=list)


class SnapshotMetadata(BaseModel):
    repositoryId: str
    snapshotId: str
    branchName: str | None = None
    commitSha: str | None = None


class ModelMetadata(BaseModel):
    provider: str = "gemini"
    model: str
    promptVersion: str
    orchestrationVersion: str


class GroundedQueryRequest(BaseModel):
    """Repository context loaded by Spring plus the user's minimal query."""

    repositoryId: str
    query: str = Field(min_length=1, max_length=4000)
    targetNodeId: str | None = None
    stackTrace: str | None = Field(default=None, max_length=20000)
    errorLog: str | None = Field(default=None, max_length=20000)
    graph: GraphPayload
    history: list[HistoricalFailure] = Field(default_factory=list)
    repositoryMetadata: dict[str, Any] = Field(default_factory=dict)
    snapshotMetadata: SnapshotMetadata


class GroundedQueryResponse(ExplanationResponse):
    snapshotMetadata: SnapshotMetadata
    modelMetadata: ModelMetadata
