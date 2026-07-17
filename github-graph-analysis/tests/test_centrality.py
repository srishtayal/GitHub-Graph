import unittest

from app.analytics.centrality import rank_centrality
from tests.fixtures.graph_fixtures import primary_graph


class CentralityTest(unittest.TestCase):
    def test_ranks_function_bottlenecks_with_normalized_degree_scores(self) -> None:
        result = rank_centrality(
            primary_graph(),
            node_types={"function"},
            limit=1,
        )

        self.assertEqual(3, result.eligibleNodeCount)
        self.assertEqual(1, len(result.rankedNodes))
        score = result.rankedNodes[0]
        self.assertEqual("function:process", score.node.nodeId)
        self.assertEqual(1, score.inDegree)
        self.assertEqual(1, score.outDegree)
        self.assertEqual(2, score.totalDegree)
        self.assertEqual(1.0, score.centralityScore)

    def test_handles_single_or_empty_rank_limits_without_dividing_by_zero(self) -> None:
        result = rank_centrality(
            primary_graph(),
            node_types={"module"},
            include_external=True,
        )
        empty_result = rank_centrality(primary_graph(), limit=0)

        self.assertEqual(1, result.eligibleNodeCount)
        self.assertEqual(0.0, result.rankedNodes[0].centralityScore)
        self.assertEqual([], empty_result.rankedNodes)
        with self.assertRaisesRegex(ValueError, "limit"):
            rank_centrality(primary_graph(), limit=-1)


if __name__ == "__main__":
    unittest.main()
