import unittest

from app.analytics.topology import topological_sort
from tests.fixtures.graph_fixtures import acyclic_graph, primary_graph


class TopologicalSortTest(unittest.TestCase):
    def test_returns_prerequisites_before_dependents_when_acyclic(self) -> None:
        result = topological_sort(acyclic_graph())
        ordered_node_ids = [node.nodeId for node in result.topologicalOrder]

        self.assertTrue(result.isAcyclic)
        self.assertEqual(ordered_node_ids, [node.nodeId for node in result.partialOrder])
        self.assertEqual([], result.blockedNodeIds)
        self.assertLess(ordered_node_ids.index("file:model"), ordered_node_ids.index("file:service"))
        self.assertLess(ordered_node_ids.index("file:service"), ordered_node_ids.index("file:api"))
        self.assertLess(ordered_node_ids.index("function:handler"), ordered_node_ids.index("file:api"))

    def test_returns_partial_order_and_cycles_when_blocked(self) -> None:
        result = topological_sort(primary_graph())

        self.assertFalse(result.isAcyclic)
        self.assertEqual([], result.topologicalOrder)
        self.assertEqual({"file:cycle-a", "file:cycle-b"}, set(result.blockedNodeIds))
        self.assertIn("file:model", [node.nodeId for node in result.partialOrder])
        self.assertEqual(
            ["file:cycle-a", "file:cycle-b", "file:cycle-a"],
            result.cycles[0].nodeIds,
        )


if __name__ == "__main__":
    unittest.main()
