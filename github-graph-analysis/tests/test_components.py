import unittest

from app.analytics.components import find_connected_components
from tests.fixtures.graph_fixtures import primary_graph


class ConnectedComponentsTest(unittest.TestCase):
    def test_groups_structural_clusters_and_isolated_nodes(self) -> None:
        result = find_connected_components(primary_graph())

        self.assertEqual(3, result.componentCount)
        self.assertEqual([6, 2, 1], [component.nodeCount for component in result.components])
        self.assertEqual(
            ["file:api", "file:model", "file:service", "function:handler", "function:helper", "function:process"],
            [node.nodeId for node in result.components[0].nodes],
        )
        self.assertEqual(
            ["file:cycle-a", "file:cycle-b"],
            [node.nodeId for node in result.components[1].nodes],
        )
        self.assertEqual(["file:orphan"], [node.nodeId for node in result.components[2].nodes])
        self.assertNotIn(
            "repo:sample",
            [node.nodeId for component in result.components for node in component.nodes],
        )


if __name__ == "__main__":
    unittest.main()
