"""Transitive threshold-based clustering over weighted similarity links."""

from __future__ import annotations

import hashlib
from collections import defaultdict, deque

from app.schemas.responses import GraphPayload
from app.schemas.similarity import (
    ClusterResult,
    FeatureProfile,
    SimilarityCluster,
    SimilarityLink,
    SimilarityNodeType,
)
from app.services.graph_feature_extractor import GraphFeatureExtractor
from app.services.similarity_engine import SimilarityEngine, _profile_for


class SimilarityClusterer:
    """Create deterministic connected-component clusters from similarity links."""

    def __init__(
        self,
        similarity_engine: SimilarityEngine | None = None,
        feature_extractor: GraphFeatureExtractor | None = None,
    ) -> None:
        self.feature_extractor = feature_extractor or GraphFeatureExtractor()
        self.similarity_engine = similarity_engine or SimilarityEngine(self.feature_extractor)

    def cluster(
        self,
        graph: GraphPayload,
        node_type: SimilarityNodeType,
        threshold: float,
        profile: FeatureProfile | None = None,
    ) -> ClusterResult:
        if not 0.0 <= threshold <= 1.0:
            raise ValueError("Similarity threshold must be between 0.0 and 1.0")

        index = self.feature_extractor.build_index(graph)
        node_ids = sorted(
            node_id
            for node_id, node in index.projection.nodes_by_id.items()
            if node.type == node_type
        )
        selected_profile = profile or _profile_for(node_type)
        links: list[SimilarityLink] = []
        adjacency: dict[str, set[str]] = defaultdict(set)

        for position, source_node_id in enumerate(node_ids):
            source = index.features_for(source_node_id)
            for target_node_id in node_ids[position + 1 :]:
                result = self.similarity_engine.compare(
                    source,
                    index.features_for(target_node_id),
                    selected_profile,
                )
                if result.score >= threshold:
                    links.append(
                        SimilarityLink(
                            sourceNodeId=source_node_id,
                            targetNodeId=target_node_id,
                            score=result.score,
                        )
                    )
                    adjacency[source_node_id].add(target_node_id)
                    adjacency[target_node_id].add(source_node_id)

        clusters = [
            SimilarityCluster(
                clusterId=_cluster_id(node_type, members),
                nodeType=node_type,
                threshold=threshold,
                memberNodeIds=members,
                links=[
                    link
                    for link in links
                    if link.sourceNodeId in members and link.targetNodeId in members
                ],
            )
            for members in _connected_components(node_ids, adjacency)
        ]
        return ClusterResult(nodeType=node_type, threshold=threshold, clusters=clusters)


def cluster_id_by_node(result: ClusterResult) -> dict[str, str]:
    """Create the optional node-to-cluster mapping accepted by similarity ranking."""
    return {
        node_id: cluster.clusterId
        for cluster in result.clusters
        for node_id in cluster.memberNodeIds
    }


def _connected_components(
    node_ids: list[str],
    adjacency: dict[str, set[str]],
) -> list[list[str]]:
    remaining = set(node_ids)
    components: list[list[str]] = []
    while remaining:
        start = min(remaining)
        queue = deque([start])
        members: list[str] = []
        remaining.remove(start)
        while queue:
            current = queue.popleft()
            members.append(current)
            for neighbor in sorted(adjacency.get(current, ())):
                if neighbor in remaining:
                    remaining.remove(neighbor)
                    queue.append(neighbor)
        components.append(sorted(members))
    return sorted(components, key=lambda members: members[0])


def _cluster_id(node_type: str, members: list[str]) -> str:
    identity = "|".join([node_type, *members])
    digest = hashlib.sha1(identity.encode("utf-8")).hexdigest()[:16]
    return f"{node_type}-cluster:{digest}"
