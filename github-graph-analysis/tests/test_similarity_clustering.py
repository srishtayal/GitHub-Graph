import unittest

from app.schemas.responses import GraphEdge, GraphNode, GraphPayload
from app.schemas.similarity import FeatureProfile
from app.services.similarity_clustering import SimilarityClusterer, cluster_id_by_node
from app.services.similarity_engine import SimilarityEngine


class SimilarityClustererTest(unittest.TestCase):
    def setUp(self) -> None:
        self.graph = _transitive_function_graph()
        self.clusterer = SimilarityClusterer()
        self.profile = FeatureProfile(name="calls-only", weights={"calledNodes": 1.0})

    def test_groups_threshold_links_as_transitive_components(self) -> None:
        result = self.clusterer.cluster(self.graph, "function", threshold=0.5, profile=self.profile)

        self.assertEqual(2, len(result.clusters))
        related = next(cluster for cluster in result.clusters if "function:a" in cluster.memberNodeIds)
        self.assertEqual(["function:a", "function:b", "function:c"], related.memberNodeIds)
        self.assertEqual(
            {("function:a", "function:b"), ("function:b", "function:c")},
            {(link.sourceNodeId, link.targetNodeId) for link in related.links},
        )

    def test_creates_deterministic_singleton_cluster_for_isolated_node(self) -> None:
        first = self.clusterer.cluster(self.graph, "function", threshold=0.5, profile=self.profile)
        second = self.clusterer.cluster(self.graph, "function", threshold=0.5, profile=self.profile)

        isolated = next(cluster for cluster in first.clusters if cluster.memberNodeIds == ["function:d"])
        self.assertEqual(isolated.clusterId, next(
            cluster.clusterId for cluster in second.clusters if cluster.memberNodeIds == ["function:d"]
        ))

    def test_rejects_invalid_threshold(self) -> None:
        with self.assertRaisesRegex(ValueError, "threshold must be between"):
            self.clusterer.cluster(self.graph, "function", threshold=1.01)

    def test_ranking_can_include_cluster_ids(self) -> None:
        clusters = self.clusterer.cluster(self.graph, "function", threshold=0.5, profile=self.profile)
        mapping = cluster_id_by_node(clusters)

        ranking = SimilarityEngine().rank_similar(
            self.graph,
            "function:a",
            profile=self.profile,
            cluster_ids=mapping,
        )

        self.assertEqual(mapping["function:b"], ranking.results[0].clusterId)


def _transitive_function_graph() -> GraphPayload:
    return GraphPayload(
        nodes=[
            GraphNode(id="function:a", type="function", label="a"),
            GraphNode(id="function:b", type="function", label="b"),
            GraphNode(id="function:c", type="function", label="c"),
            GraphNode(id="function:d", type="function", label="d"),
            GraphNode(id="module:x", type="module", label="x"),
            GraphNode(id="module:y", type="module", label="y"),
        ],
        edges=[
            GraphEdge(id="edge:01", source="function:a", target="module:x", type="CALLS"),
            GraphEdge(id="edge:02", source="function:b", target="module:x", type="CALLS"),
            GraphEdge(id="edge:03", source="function:b", target="module:y", type="CALLS"),
            GraphEdge(id="edge:04", source="function:c", target="module:y", type="CALLS"),
        ],
    )


if __name__ == "__main__":
    unittest.main()
