"""Weighted Jaccard comparison and same-type similarity ranking."""

from __future__ import annotations

from collections.abc import Mapping

from app.schemas.responses import GraphPayload
from app.schemas.similarity import (
    DEFAULT_SIMILARITY_PROFILES,
    FeatureProfile,
    FeatureSimilarity,
    NodeFeatures,
    SimilarityRanking,
    SimilarityResult,
)
from app.services.graph_feature_extractor import GraphFeatureExtractor


class SimilarityEngine:
    def __init__(self, feature_extractor: GraphFeatureExtractor | None = None) -> None:
        self.feature_extractor = feature_extractor or GraphFeatureExtractor()

    def compare(
        self,
        target: NodeFeatures,
        candidate: NodeFeatures,
        profile: FeatureProfile,
    ) -> SimilarityResult:
        if target.nodeType != candidate.nodeType:
            raise ValueError("Similarity comparisons require matching node types")

        included_weight = 0.0
        weighted_score = 0.0
        feature_scores: dict[str, FeatureSimilarity] = {}
        for feature_name, weight in profile.weights.items():
            if weight == 0:
                continue
            target_values = target.features.get(feature_name)
            candidate_values = candidate.features.get(feature_name)
            if target_values is None or candidate_values is None:
                continue
            score, matched = _jaccard(target_values, candidate_values)
            feature_scores[feature_name] = FeatureSimilarity(
                score=score,
                matchedFeatures=sorted(matched),
            )
            included_weight += weight
            weighted_score += score * weight

        return SimilarityResult(
            targetNodeId=target.nodeId,
            candidateNodeId=candidate.nodeId,
            nodeType=target.nodeType,
            score=weighted_score / included_weight if included_weight else 0.0,
            featureScores=feature_scores,
        )

    def rank_similar(
        self,
        graph: GraphPayload,
        target_node_id: str,
        limit: int = 10,
        profile: FeatureProfile | None = None,
        cluster_ids: Mapping[str, str] | None = None,
    ) -> SimilarityRanking:
        if limit < 0:
            raise ValueError("limit must be zero or greater")
        index = self.feature_extractor.build_index(graph)
        target = index.features_for(target_node_id)
        selected_profile = profile or _profile_for(target.nodeType)
        results = [
            self.compare(target, index.features_for(node_id), selected_profile)
            for node_id, node in index.projection.nodes_by_id.items()
            if node.type == target.nodeType and node_id != target_node_id
        ]
        if cluster_ids is not None:
            for result in results:
                result.clusterId = cluster_ids.get(result.candidateNodeId)
        results.sort(key=lambda result: (-result.score, result.candidateNodeId))
        return SimilarityRanking(
            targetNodeId=target.nodeId,
            nodeType=target.nodeType,
            results=results[:limit],
        )


def _jaccard(left: set[str], right: set[str]) -> tuple[float, set[str]]:
    union = left | right
    if not union:
        return 0.0, set()
    intersection = left & right
    return len(intersection) / len(union), intersection


def _profile_for(node_type: str) -> FeatureProfile:
    try:
        return getattr(DEFAULT_SIMILARITY_PROFILES, node_type)
    except AttributeError as error:
        raise ValueError(f"Unsupported similarity node type: {node_type}") from error
