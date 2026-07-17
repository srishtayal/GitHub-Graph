"""Explainable graph and history based root-cause ranking."""

from __future__ import annotations

from collections import deque
from collections.abc import Sequence

from app.analytics.graph_projection import GraphProjection
from app.schemas.failure_analysis import (
    BugLocalizationResult,
    FailureInput,
    HistoricalFailure,
    LocalizationConfiguration,
    RootCauseCandidate,
    RootCauseReason,
    SimilarPastFailure,
)
from app.schemas.responses import GraphPayload
from app.schemas.similarity import DEFAULT_SIMILARITY_PROFILES, NodeFeatures
from app.services.failure_path_parser import FailurePathParser
from app.services.graph_feature_extractor import GraphFeatureExtractor
from app.services.similarity_engine import SimilarityEngine


class BugLocalizer:
    def __init__(
        self,
        parser: FailurePathParser | None = None,
        feature_extractor: GraphFeatureExtractor | None = None,
        similarity_engine: SimilarityEngine | None = None,
    ) -> None:
        self.parser = parser or FailurePathParser()
        self.feature_extractor = feature_extractor or GraphFeatureExtractor()
        self.similarity_engine = similarity_engine or SimilarityEngine(self.feature_extractor)

    def localize(
        self,
        graph: GraphPayload,
        failure: FailureInput,
        history: Sequence[HistoricalFailure],
        configuration: LocalizationConfiguration | None = None,
    ) -> BugLocalizationResult:
        config = configuration or LocalizationConfiguration()
        projection = GraphProjection.from_payload(graph)
        resolved = self.parser.parse(projection, failure)
        index = self.feature_extractor.build_index(projection)
        distances = _impacted_distances(projection, resolved.nodeIds, config.maxTraversalDepth)
        impacted_ids = sorted(distances)
        comparable_history = [record for record in history if record.repositoryId == failure.repositoryId]
        past_failures = self._similar_history(index, resolved, comparable_history, config.maxPastFailures)
        history_scores = _history_scores(past_failures, comparable_history)
        candidates = _rank_candidates(
            projection,
            resolved.nodeIds,
            resolved.stackFrameNodeIds,
            distances,
            history_scores,
            config,
        )
        return BugLocalizationResult(
            resolvedFailurePath=resolved,
            impactedNodeIds=impacted_ids,
            similarPastFailures=[item[0] for item in past_failures],
            suspectedRootCauses=candidates[: config.maxSuspectedRootCauses],
            reasoningMetadata={
                "historyRecordsCompared": len(comparable_history),
                "maxTraversalDepth": config.maxTraversalDepth,
                "candidateCount": len(candidates),
            },
        )

    def _similar_history(self, index, resolved, history, limit):
        current = _failure_features("current", index, resolved.nodeIds, resolved.errorSignature)
        comparisons = []
        for record in history:
            prior = _failure_features(record.failureId, index, record.failurePathNodeIds, record.errorSignature)
            result = self.similarity_engine.compare(current, prior, DEFAULT_SIMILARITY_PROFILES.failurePath)
            comparisons.append((SimilarPastFailure(failureId=record.failureId, similarity=result.score), record))
        return sorted(comparisons, key=lambda item: (-item[0].similarity, item[0].failureId))[:limit]


def _failure_features(failure_id, index, node_ids, signature) -> NodeFeatures:
    files: set[str] = set()
    dependencies: set[str] = set()
    for node_id in node_ids:
        node = index.projection.node(node_id)
        if node is not None and node.type == "file":
            files.add(f"file:{node_id}")
        enclosing = index.enclosing_file_by_node.get(node_id)
        if enclosing:
            files.add(f"file:{enclosing}")
        for edge in index.projection.outgoing_edges(node_id) + index.projection.incoming_edges(node_id):
            other = edge.target if edge.source == node_id else edge.source
            other_node = index.projection.node(other)
            if other_node and other_node.type == "module":
                dependencies.add(f"dependency:{other}")
    signature_features = set()
    if signature.exceptionType:
        signature_features.add(f"exception:{signature.exceptionType}")
    if signature.messageFingerprint:
        signature_features.add(f"message:{signature.messageFingerprint}")
    return NodeFeatures(
        nodeId=failure_id,
        nodeType="failurePath",
        features={
            "pathNodes": {f"path:{node_id}" for node_id in node_ids},
            "touchedFiles": files,
            "dependencies": dependencies,
            "errorSignature": signature_features,
        },
    )


def _impacted_distances(projection, seed_ids, max_depth):
    distances: dict[str, int] = {}
    queue = deque()
    for node_id in seed_ids:
        if projection.contains_node(node_id):
            distances[node_id] = 0
            queue.append(node_id)
    while queue:
        current = queue.popleft()
        if distances[current] >= max_depth:
            continue
        neighbors = {edge.target for edge in projection.outgoing_edges(current)} | {
            edge.source for edge in projection.incoming_edges(current)
        }
        for neighbor in sorted(neighbors):
            if neighbor not in distances:
                distances[neighbor] = distances[current] + 1
                queue.append(neighbor)
    return distances


def _history_scores(past_failures, history):
    by_id = {record.failureId: record for record in history}
    scores: dict[str, float] = {}
    for comparison, _ in past_failures:
        record = by_id[comparison.failureId]
        for node_id in record.confirmedRootCauseNodeIds:
            scores[node_id] = max(scores.get(node_id, 0.0), comparison.similarity)
    return scores


def _rank_candidates(projection, path_ids, stack_ids, distances, history_scores, config):
    weights = {
        "on_failure_path": config.pathEvidenceWeight,
        "stack_frame_match": config.stackEvidenceWeight,
        "historical_failure_overlap": config.historyEvidenceWeight,
        "structural_proximity": config.structuralEvidenceWeight,
        "graph_criticality": config.criticalityEvidenceWeight,
    }
    denominator = sum(weights.values())
    eligible = [
        node_id for node_id in distances
        if projection.node(node_id).type != "repo"
    ]
    max_degree = max((_degree(projection, node_id) for node_id in eligible), default=1)
    candidates = []
    for node_id in eligible:
        evidence = {
            "on_failure_path": 1.0 if node_id in path_ids else 0.0,
            "stack_frame_match": 1.0 if node_id in stack_ids else 0.0,
            "historical_failure_overlap": history_scores.get(node_id, 0.0),
            "structural_proximity": 1.0 - (distances[node_id] / (config.maxTraversalDepth + 1)),
            "graph_criticality": _degree(projection, node_id) / max_degree,
        }
        reasons = [
            RootCauseReason(kind=kind, weight=(weights[kind] * value) / denominator)
            for kind, value in evidence.items()
            if value > 0 and weights[kind] > 0
        ]
        score = sum(reason.weight for reason in reasons)
        candidates.append(
            RootCauseCandidate(
                nodeId=node_id,
                score=score,
                confidence=_confidence(score, evidence),
                reasons=reasons,
            )
        )
    return sorted(candidates, key=lambda item: (-item.score, item.nodeId))


def _degree(projection, node_id):
    return len(projection.outgoing_edges(node_id)) + len(projection.incoming_edges(node_id))


def _confidence(score, evidence):
    direct_evidence = evidence["on_failure_path"] or evidence["stack_frame_match"]
    if score >= 0.70 and direct_evidence:
        return "high"
    if score >= 0.35 and direct_evidence:
        return "medium"
    return "low"
