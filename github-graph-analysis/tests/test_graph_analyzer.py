import unittest

from app.schemas.responses import GraphEdge, GraphNode, GraphPayload
from app.services.graph_analyzer import GraphAnalyzer, analyze_graph, resolve_node_id


def _sample_graph() -> GraphPayload:
    return GraphPayload(
        nodes=[
            GraphNode(id="file:a", type="file", label="a.py"),
            GraphNode(id="file:b", type="file", label="b.py"),
            GraphNode(id="file:c", type="file", label="c.py"),
            GraphNode(id="fn:main", type="function", label="a.main"),
            GraphNode(id="fn:helper", type="function", label="b.helper"),
            GraphNode(id="fn:leaf", type="function", label="c.leaf"),
        ],
        edges=[
            GraphEdge(id="e1", source="file:a", target="file:b", type="USES"),
            GraphEdge(id="e2", source="file:b", target="file:c", type="USES"),
            GraphEdge(id="e3", source="file:c", target="file:a", type="USES"),
            GraphEdge(id="e4", source="fn:main", target="file:a", type="BELONGS_TO"),
            GraphEdge(id="e5", source="fn:helper", target="file:b", type="BELONGS_TO"),
            GraphEdge(id="e6", source="fn:leaf", target="file:c", type="BELONGS_TO"),
            GraphEdge(id="e7", source="fn:main", target="fn:helper", type="CALLS"),
            GraphEdge(id="e8", source="fn:helper", target="fn:leaf", type="CALLS"),
        ],
    )


class GraphAnalyzerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.graph = _sample_graph()
        self.analyzer = GraphAnalyzer(self.graph)

    def test_dfs_trace_dependencies(self) -> None:
        result = self.analyzer.dfs_trace_dependencies("fn:main", max_depth=5)
        self.assertEqual("fn:main", result["source"])
        self.assertGreater(result["total_dependencies"], 0)
        self.assertTrue(any(path["target"] == "fn:helper" for path in result["dependency_paths"]))

    def test_bfs_impact_spread(self) -> None:
        result = self.analyzer.bfs_impact_spread("fn:leaf", max_depth=5)
        self.assertEqual("fn:leaf", result["source"])
        self.assertGreaterEqual(result["total_affected"], 2)
        self.assertGreaterEqual(result["impact_radius"], 1)

    def test_connected_components(self) -> None:
        result = self.analyzer.find_connected_components()
        self.assertEqual(1, result["total_components"])
        self.assertEqual(6, result["largest_component_size"])

    def test_topological_sort_detects_cycle(self) -> None:
        result = self.analyzer.topological_sort()
        self.assertFalse(result["is_acyclic"])
        self.assertGreater(len(result["unordered_nodes"]), 0)

    def test_centrality_ranks_nodes(self) -> None:
        result = self.analyzer.compute_centrality()
        self.assertEqual(6, result["total_nodes_analyzed"])
        self.assertGreater(result["top_critical_nodes"][0]["importance"], 0)

    def test_detect_cycles(self) -> None:
        result = self.analyzer.detect_cycles()
        self.assertTrue(result["has_cycles"])
        self.assertGreater(result["total_cycles"], 0)

    def test_resolve_node_id_by_label(self) -> None:
        self.assertEqual("file:b", resolve_node_id(self.graph, "b.py"))

    def test_analyze_graph_returns_insights(self) -> None:
        result = analyze_graph(self.graph, node_id="b.py", max_depth=5)
        self.assertIn("insights", result)
        self.assertIn("most_critical_functions", result["insights"])
        self.assertIsNotNone(result["node_analysis"])
        self.assertEqual("file:b", result["node_analysis"]["node_id"])


if __name__ == "__main__":
    unittest.main()
