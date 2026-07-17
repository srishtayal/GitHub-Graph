import unittest

from app.services.graph_analytics_service import GraphAnalyticsService
from tests.fixtures.graph_fixtures import primary_graph


class GraphAnalyticsServiceTest(unittest.TestCase):
    def test_exposes_stable_node_id_analytics_operations(self) -> None:
        service = GraphAnalyticsService()
        graph = primary_graph()

        dependency_trace = service.trace_dependencies(graph, "file:api")
        impact = service.analyze_impact(graph, "function:helper")
        components = service.find_connected_components(graph)
        topology = service.topological_sort(graph)
        centrality = service.rank_centrality(graph, node_types={"function"})
        cycles = service.detect_cycles(graph)

        self.assertEqual("file:api", dependency_trace.queryNodeId)
        self.assertEqual("function:helper", impact.queryNodeId)
        self.assertEqual(3, components.componentCount)
        self.assertFalse(topology.isAcyclic)
        self.assertEqual("function:process", centrality.rankedNodes[0].node.nodeId)
        self.assertTrue(cycles.hasCycles)


if __name__ == "__main__":
    unittest.main()
