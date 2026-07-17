import unittest

from app.analytics.traversal import analyze_impact, trace_dependencies
from tests.fixtures.graph_fixtures import primary_graph


class TraversalTest(unittest.TestCase):
    def test_dfs_traces_outgoing_dependencies_in_stable_order(self) -> None:
        result = trace_dependencies(primary_graph(), "file:api")

        self.assertEqual(
            [
                "file:api",
                "file:service",
                "file:model",
                "function:handler",
                "function:process",
                "function:helper",
            ],
            [node.nodeId for node in result.visitedNodes],
        )
        helper = result.visitedNodes[-1]
        self.assertEqual(3, helper.depth)
        self.assertEqual("function:process", helper.predecessorNodeId)
        self.assertEqual("CALLS", helper.viaEdgeType)

    def test_bfs_reports_dependents_and_respects_depth_limit(self) -> None:
        result = analyze_impact(primary_graph(), "function:helper", max_depth=2)

        self.assertEqual(
            ["function:helper", "function:process", "function:handler"],
            [node.nodeId for node in result.visitedNodes],
        )
        self.assertEqual(
            ["function:process", "function:handler"],
            [node.nodeId for node in result.impactedNodes],
        )
        self.assertEqual(2, result.dependencyDepth)

    def test_external_modules_are_opt_in_and_unknown_ids_fail_clearly(self) -> None:
        without_external = trace_dependencies(
            primary_graph(), "file:api", edge_types={"IMPORTS"}
        )
        with_external = trace_dependencies(
            primary_graph(),
            "file:api",
            edge_types={"IMPORTS"},
            include_external=True,
        )

        self.assertEqual(["file:api"], [node.nodeId for node in without_external.visitedNodes])
        self.assertEqual(
            ["file:api", "module:requests"],
            [node.nodeId for node in with_external.visitedNodes],
        )
        with self.assertRaisesRegex(ValueError, "Unknown graph node ID"):
            trace_dependencies(primary_graph(), "file:missing")
        with self.assertRaisesRegex(ValueError, "max_depth"):
            analyze_impact(primary_graph(), "file:api", max_depth=-1)


if __name__ == "__main__":
    unittest.main()
