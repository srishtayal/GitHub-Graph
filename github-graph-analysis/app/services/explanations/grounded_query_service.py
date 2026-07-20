"""Compute repository intelligence before asking the grounded explanation layer."""

import os

from app.schemas.explanations import (
    ExplanationRequest,
    GroundedQueryRequest,
    GroundedQueryResponse,
    ModelMetadata,
)
from app.schemas.failure_analysis import FailureInput, LocalizationConfiguration
from app.services.bug_localizer import BugLocalizer
from app.services.explanations.explanation_service import ExplanationService
from app.services.explanations.query_router import QueryRouter
from app.services.explanations.target_resolver import TargetResolver
from app.services.graph_analytics_service import GraphAnalyticsService
from app.services.similarity_engine import SimilarityEngine


PROMPT_VERSION = "phase7-grounded-v2"
ORCHESTRATION_VERSION = "phase7-orchestration-v1"


class GroundedQueryService:
    """Route, analyze, bound, and explain a repository question."""

    def __init__(
        self,
        explanation_service: ExplanationService | None = None,
        router: QueryRouter | None = None,
        target_resolver: TargetResolver | None = None,
        analytics: GraphAnalyticsService | None = None,
        similarity: SimilarityEngine | None = None,
        bug_localizer: BugLocalizer | None = None,
    ) -> None:
        self._explanation_service = explanation_service or ExplanationService()
        self._router = router or QueryRouter()
        self._target_resolver = target_resolver or TargetResolver()
        self._analytics = analytics or GraphAnalyticsService()
        self._similarity = similarity or SimilarityEngine()
        self._bug_localizer = bug_localizer or BugLocalizer()

    def query(self, request: GroundedQueryRequest) -> GroundedQueryResponse:
        intent = self._router.route(request.query)
        analysis_request = ExplanationRequest(
            query=request.query,
            repositoryId=request.repositoryId,
            repositoryMetadata=request.repositoryMetadata,
            graph=request.graph,
        )
        if intent == "unknown_or_insufficient":
            return self._insufficient(
                request,
                intent,
                "The question does not map to a supported repository-analysis intent.",
            )

        target = None
        target_error = None
        if intent in {"dependency_flow", "impact_analysis", "similarity"}:
            target, target_error = self._target_resolver.resolve(
                request.graph,
                request.query,
                request.targetNodeId,
            )
        elif request.targetNodeId:
            target, target_error = self._target_resolver.resolve(
                request.graph,
                request.query,
                request.targetNodeId,
            )
        elif intent == "bug_explanation":
            target, _ = self._target_resolver.resolve(
                request.graph,
                request.query,
                None,
            )

        if target_error:
            return self._insufficient(request, intent, target_error)

        try:
            if intent == "dependency_flow" and target:
                analysis_request.dependencyTrace = self._analytics.trace_dependencies(
                    request.graph,
                    target.id,
                    max_depth=4,
                )
            elif intent == "impact_analysis" and target:
                analysis_request.impactAnalysis = self._analytics.analyze_impact(
                    request.graph,
                    target.id,
                    max_depth=4,
                )
            elif intent == "criticality":
                analysis_request.centrality = self._analytics.rank_centrality(
                    request.graph,
                    limit=20,
                )
            elif intent == "similarity" and target:
                analysis_request.similarityRanking = self._similarity.rank_similar(
                    request.graph,
                    target.id,
                    limit=10,
                )
            elif intent == "bug_explanation":
                failure = self._failure_input(request, target)
                if failure is None:
                    return self._insufficient(
                        request,
                        intent,
                        "Bug localization requires a target node, stack trace, or error log.",
                    )
                analysis_request.bugLocalization = self._bug_localizer.localize(
                    request.graph,
                    failure,
                    request.history,
                    LocalizationConfiguration(
                        maxTraversalDepth=3,
                        maxPastFailures=10,
                        maxSuspectedRootCauses=10,
                    ),
                )
            elif intent == "cycle_or_order":
                analysis_request.cycleDetection = self._analytics.detect_cycles(request.graph)
                analysis_request.topologicalSort = self._analytics.topological_sort(request.graph)
        except ValueError as error:
            return self._insufficient(request, intent, str(error))

        explanation = self._explanation_service.explain(analysis_request)
        return GroundedQueryResponse(
            **explanation.model_dump(mode="python"),
            snapshotMetadata=request.snapshotMetadata,
            modelMetadata=self._model_metadata(),
        )

    def _failure_input(self, request: GroundedQueryRequest, target) -> FailureInput | None:
        failing_node_id = target.id if target else request.targetNodeId
        if not any((failing_node_id, request.errorLog, request.stackTrace)):
            return None
        return FailureInput(
            repositoryId=request.repositoryId,
            failingNodeId=failing_node_id,
            errorLog=request.errorLog,
            stackTrace=request.stackTrace,
            failurePathNodeIds=[failing_node_id] if failing_node_id else [],
        )

    def _insufficient(self, request, intent, limitation: str) -> GroundedQueryResponse:
        return GroundedQueryResponse(
            intent=intent,
            answer="I cannot answer this question from the available repository evidence.",
            confidence="insufficient",
            limitations=[limitation],
            followUpSuggestions=[
                "Name a file, function, class, API, or module from this snapshot and ask again."
            ],
            snapshotMetadata=request.snapshotMetadata,
            modelMetadata=self._model_metadata(),
        )

    @staticmethod
    def _model_metadata() -> ModelMetadata:
        return ModelMetadata(
            model=os.environ.get("GEMINI_MODEL", "gemini-3.1-flash-lite"),
            promptVersion=PROMPT_VERSION,
            orchestrationVersion=ORCHESTRATION_VERSION,
        )
