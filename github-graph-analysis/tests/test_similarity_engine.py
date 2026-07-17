import unittest

from app.schemas.similarity import FeatureProfile, NodeFeatures
from app.services.similarity_engine import SimilarityEngine
from tests.fixtures.phase6_graphs import similarity_graph


class SimilarityEngineTest(unittest.TestCase):
    def setUp(self) -> None:
        self.engine = SimilarityEngine()

    def test_computes_weighted_jaccard_and_reports_matches(self) -> None:
        profile = FeatureProfile(name="test", weights={"calls": 0.75, "imports": 0.25})
        target = NodeFeatures(
            nodeId="function:one",
            nodeType="function",
            features={"calls": {"a", "b"}, "imports": {"jwt"}},
        )
        candidate = NodeFeatures(
            nodeId="function:two",
            nodeType="function",
            features={"calls": {"b", "c"}, "imports": {"jwt"}},
        )

        result = self.engine.compare(target, candidate, profile)

        self.assertEqual(0.5, result.score)
        self.assertEqual(["b"], result.featureScores["calls"].matchedFeatures)
        self.assertEqual(1.0, result.featureScores["imports"].score)

    def test_empty_sets_do_not_match(self) -> None:
        profile = FeatureProfile(name="test", weights={"calls": 1.0})
        target = NodeFeatures(nodeId="function:one", nodeType="function", features={"calls": set()})
        candidate = NodeFeatures(nodeId="function:two", nodeType="function", features={"calls": set()})

        result = self.engine.compare(target, candidate, profile)

        self.assertEqual(0.0, result.score)

    def test_ranks_same_type_candidates_and_excludes_target(self) -> None:
        ranking = self.engine.rank_similar(similarity_graph(), "function:login-a")

        self.assertEqual("function", ranking.nodeType)
        self.assertEqual("function:login-b", ranking.results[0].candidateNodeId)
        self.assertNotIn("function:login-a", [result.candidateNodeId for result in ranking.results])
        self.assertTrue(all(result.nodeType == "function" for result in ranking.results))

    def test_rejects_mixed_node_types_and_negative_limits(self) -> None:
        profile = FeatureProfile(name="test", weights={"calls": 1.0})
        function = NodeFeatures(nodeId="function:one", nodeType="function", features={"calls": set()})
        file = NodeFeatures(nodeId="file:one", nodeType="file", features={"calls": set()})

        with self.assertRaisesRegex(ValueError, "matching node types"):
            self.engine.compare(function, file, profile)
        with self.assertRaisesRegex(ValueError, "limit must be zero"):
            self.engine.rank_similar(similarity_graph(), "function:login-a", limit=-1)


if __name__ == "__main__":
    unittest.main()
